package vdt.mini.shared_lib.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;
import vdt.mini.shared_lib.mq.SecurityRecordInterceptor;
import vdt.mini.shared_lib.service.SecurityIdGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityAutoConfigurationTest {
    @Test
    void securityKafkaListenerContainerFactoryPostProcessor_shouldRegisterSecurityInterceptorWithoutDroppingExistingOne() {
        SecurityAutoConfiguration configuration = new SecurityAutoConfiguration();
        SecurityRecordInterceptor securityInterceptor = mock(SecurityRecordInterceptor.class);
        RecordInterceptor<Object, Object> existingInterceptor = (record, consumer) -> record;
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setRecordInterceptor(existingInterceptor);

        BeanPostProcessor postProcessor = configuration.securityKafkaListenerContainerFactoryPostProcessor(securityInterceptor);
        postProcessor.postProcessBeforeInitialization(factory, "kafkaListenerContainerFactory");

        assertThat(factory.getRecordInterceptor()).isInstanceOf(CompositeRecordInterceptor.class);
    }

    @Test
    void redisSyncConfig_shouldResolveDeterministicServiceIdForListenerChannels() {
        assertThat(SecurityAutoConfiguration.RedisSyncConfig.resolveDeterministicServiceId("mini-project", "user-service"))
                .isEqualTo(SecurityIdGenerator.serviceId("mini-project", "user-service"));
    }
}
