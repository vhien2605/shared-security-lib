package vdt.mini.shared_lib.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import vdt.mini.shared_lib.service.RedisSecurityRuntimeSubscriber;
import vdt.mini.shared_lib.service.RedisSecurityRuntimeKeys;
import vdt.mini.shared_lib.service.RedisSettingsSubscriber;
import vdt.mini.shared_lib.service.SecurityIdGenerator;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.mq.KafkaOutboundMetadataEnricher;
import vdt.mini.shared_lib.web.OutboundFeignMetadataInterceptor;
import vdt.mini.shared_lib.mq.SecurityRecordInterceptor;
import vdt.mini.shared_lib.web.OutboundContextHolder;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@AutoConfigureBefore(DataRedisAutoConfiguration.class)
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "vdt.mini.shared_lib")
@EnableConfigurationProperties(SecurityAuditLogProperties.class)
public class SecurityAutoConfiguration {

    @Value("${app.security.kafka.bootstrap-servers:localhost:9094}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, String> securityKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }

    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnMissingBean(OutboundFeignMetadataInterceptor.class)
    public OutboundFeignMetadataInterceptor outboundFeignMetadataInterceptor(OutboundContextHolder contextHolder) {
        return new OutboundFeignMetadataInterceptor(contextHolder);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaOutboundMetadataEnricher.class)
    public KafkaOutboundMetadataEnricher kafkaOutboundMetadataEnricher(OutboundContextHolder contextHolder) {
        return new KafkaOutboundMetadataEnricher(contextHolder);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnClass(AbstractKafkaListenerContainerFactory.class)
    @ConditionalOnProperty(name = "app.security.mq.inbound.enabled", havingValue = "true", matchIfMissing = true)
    public BeanPostProcessor securityKafkaListenerContainerFactoryPostProcessor(SecurityRecordInterceptor securityRecordInterceptor) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
                    configureFactory(factory);
                }
                return bean;
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            private void configureFactory(AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
                RecordInterceptor existing = factory.getRecordInterceptor();
                RecordInterceptor security = securityRecordInterceptor;
                if (existing == null) {
                    factory.setRecordInterceptor(security);
                } else if (existing instanceof CompositeRecordInterceptor composite) {
                    composite.addRecordInterceptor(security);
                } else {
                    factory.setRecordInterceptor(new CompositeRecordInterceptor(existing, security));
                }
                DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
                }, new FixedBackOff(0L, 0L));
                errorHandler.addNotRetryableExceptions(InboundSecurityException.class);
                factory.setCommonErrorHandler(errorHandler);
            }
        };
    }

    @Configuration
    @ConditionalOnProperty(name = "app.security.settings.sync.enabled", havingValue = "true", matchIfMissing = true)
    public static class RedisSyncConfig {

        @Value("${app.security.redis.host:localhost}")
        private String redisHost;

        @Value("${app.security.redis.port:6379}")
        private int redisPort;

        @Value("${app.security.redis.password:redis123}")
        private String redisPassword;

        @Value("${app.security.namespace:default}")
        private String namespace;

        @Value("${app.security.service.name:my-service}")
        private String serviceName;

        @Bean
        public RedisConnectionFactory securityRedisConnectionFactory() {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
            if (StringUtils.hasText(redisPassword)) {
                config.setPassword(redisPassword);
            }
            return new LettuceConnectionFactory(config);
        }

        @Bean
        public StringRedisTemplate securityRedisTemplate(
                RedisConnectionFactory securityRedisConnectionFactory) {
            return new StringRedisTemplate(securityRedisConnectionFactory);
        }

        @Bean
        public RedisMessageListenerContainer securityRedisListenerContainer(
                RedisConnectionFactory securityRedisConnectionFactory,
                RedisSettingsSubscriber subscriber,
                RedisSecurityRuntimeSubscriber runtimeSubscriber) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(securityRedisConnectionFactory);
            String serviceId = resolveDeterministicServiceId(namespace, serviceName);
            String settingsChannel = RedisSecurityRuntimeKeys.legacySettingsChannel(serviceId);
            String runtimeChannel = RedisSecurityRuntimeKeys.eventsChannel(serviceId);
            container.addMessageListener(subscriber, new PatternTopic(settingsChannel));
            container.addMessageListener(runtimeSubscriber, new PatternTopic(runtimeChannel));
            org.slf4j.LoggerFactory.getLogger(SecurityAutoConfiguration.class).info(
                    "Redis security listeners subscribed serviceId={} settingsChannel={} runtimeChannel={}",
                    serviceId, settingsChannel, runtimeChannel);
            return container;
        }

        static String resolveDeterministicServiceId(String namespace, String serviceName) {
            return SecurityIdGenerator.serviceId(namespace, serviceName);
        }
    }
}
