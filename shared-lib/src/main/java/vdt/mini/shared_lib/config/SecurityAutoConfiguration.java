package vdt.mini.shared_lib.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import vdt.mini.shared_lib.service.IdentityManager;
import vdt.mini.shared_lib.service.RedisSecurityRuntimeSubscriber;
import vdt.mini.shared_lib.service.RedisSecurityRuntimeKeys;
import vdt.mini.shared_lib.service.RedisSettingsSubscriber;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@AutoConfigureBefore(DataRedisAutoConfiguration.class)
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "vdt.mini.shared_lib")
public class SecurityAutoConfiguration {

    @Value("${app.security.kafka.bootstrap-servers:localhost:9094}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, String> securityKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
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
                RedisSecurityRuntimeSubscriber runtimeSubscriber,
                IdentityManager identityManager) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(securityRedisConnectionFactory);
            String serviceId = identityManager.getOrCreateServiceId();
            String settingsChannel = RedisSecurityRuntimeKeys.legacySettingsChannel(serviceId);
            String runtimeChannel = RedisSecurityRuntimeKeys.eventsChannel(serviceId);
            container.addMessageListener(subscriber, new PatternTopic(settingsChannel));
            container.addMessageListener(runtimeSubscriber, new PatternTopic(runtimeChannel));
            org.slf4j.LoggerFactory.getLogger(SecurityAutoConfiguration.class).info(
                    "Redis security listeners subscribed serviceId={} settingsChannel={} runtimeChannel={}",
                    serviceId, settingsChannel, runtimeChannel);
            return container;
        }
    }
}
