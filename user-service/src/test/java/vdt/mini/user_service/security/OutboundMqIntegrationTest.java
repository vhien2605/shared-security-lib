package vdt.mini.user_service.security;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.aspect.OutBoundSecurityAspect;
import vdt.mini.shared_lib.config.SecurityAutoConfiguration;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.shared_lib.enums.OutboundErrorCode;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.exception.OutboundException;
import vdt.mini.shared_lib.mq.KafkaSendCaptureContext;
import vdt.mini.shared_lib.mq.KafkaSendCaptureAspect;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;
import vdt.mini.shared_lib.web.OutboundExecutionPolicy;
import vdt.mini.shared_lib.web.OutboundPolicyService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = OutboundMqIntegrationTest.TestApplication.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                OutboundMqIntegrationTest.TOPIC_VOID,
                OutboundMqIntegrationTest.TOPIC_FUTURE,
                OutboundMqIntegrationTest.TOPIC_RETRY,
                OutboundMqIntegrationTest.TOPIC_MULTI,
                OutboundMqIntegrationTest.TOPIC_MULTI_AUDIT,
                OutboundMqIntegrationTest.TOPIC_THRESHOLD
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.acks=all",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.security.enabled=false",
        "app.security.mq.inbound.enabled=false",
        "app.security.settings.sync.enabled=false",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.kafka=WARN"
})
class OutboundMqIntegrationTest {
    static final String TOPIC_VOID = "it.user.outbound.void";
    static final String TOPIC_FUTURE = "it.user.outbound.future";
    static final String TOPIC_RETRY = "it.user.outbound.retry";
    static final String TOPIC_MULTI = "it.user.outbound.multi";
    static final String TOPIC_MULTI_AUDIT = "it.user.outbound.multi.audit";
    static final String TOPIC_THRESHOLD = "it.user.outbound.threshold";

    @Autowired
    private TestMqPublisher publisher;
    @Autowired
    private OutboundPolicyService policyService;
    @Autowired
    private SecurityAuditLogger auditLogger;
    @Autowired
    private TestMqListener listener;

    @BeforeEach
    void setUp() {
        Mockito.reset(policyService, auditLogger);
        publisher.reset();
        listener.clear();
    }

    @Test
    void publishVoid_shouldWaitForBrokerAckAndDeliverRecord() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_VOID, "IGNORE", 0, null));

        publisher.publishVoid("user-1");

        assertThat(listener.pollValue()).isEqualTo("user-1");
        verify(auditLogger).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishFuture_shouldWaitForBrokerAckAndDeliverRecord() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_FUTURE, "IGNORE", 0, null));

        CompletableFuture<SendResult<String, String>> result = publisher.publishFuture("future-1");

        assertThat(result).isCompleted();
        assertThat(listener.pollValue()).isEqualTo("future-1");
    }

    @Test
    void publishFuture_shouldRetryAfterFailedBrokerFutureAndDeliverRecordOnce() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_FUTURE, "IGNORE", 1, null));

        CompletableFuture<SendResult<String, String>> result = publisher.publishFutureRetryThenSuccess("future-retry-1");

        assertThat(result).isCompleted();
        assertThat(listener.pollValue()).isEqualTo("future-retry-1");
        verify(auditLogger, times(2)).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishFuture_shouldRetryAfterAckTimeoutAndReturnAckedFuture() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_FUTURE, "IGNORE", 1, 1, null));

        CompletableFuture<SendResult<String, String>> result = publisher.publishFutureTimeoutRetryThenSuccess("future-timeout-1");

        assertThat(result).isCompleted();
        verify(auditLogger, times(2)).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishVoid_shouldRetryAfterTransientFailureAndDeliverRecordOnce() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_RETRY, "IGNORE", 1, null));

        publisher.publishRetryThenSuccess("retry-1");

        assertThat(listener.pollValue()).isEqualTo("retry-1");
        verify(auditLogger, times(2)).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishVoid_shouldRetryAfterCapturedBrokerFutureFailureAndDeliverRecordOnce() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_RETRY, "IGNORE", 1, null));

        publisher.publishCapturedFutureRetryThenSuccess("captured-retry-1");

        assertThat(listener.pollValue()).isEqualTo("captured-retry-1");
        verify(auditLogger, times(2)).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishFuture_shouldNotRetryNonRetryableBrokerFutureFailure() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_FUTURE, "IGNORE", 3, null));

        CompletableFuture<SendResult<String, String>> result = publisher.publishFutureNonRetryableFailure();

        assertThat(result).isNull();
        verify(auditLogger).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishVoid_shouldReturnNullWhenRetryExhaustedAndRollbackIgnore() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_RETRY, "IGNORE", 1, null));

        publisher.publishAlwaysFail("ignored");

        verify(auditLogger, times(2)).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishVoid_shouldThrowSanitizedOutboundExceptionWhenRollbackCompensate() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_RETRY, "COMPENSATE", 0, null));

        assertThatThrownBy(() -> publisher.publishAlwaysFail("compensate"))
                .isInstanceOf(OutboundException.class)
                .hasNoCause()
                .extracting("errorCode")
                .isEqualTo(OutboundErrorCode.BROKER_UNAVAILABLE);
    }

    @Test
    void publishVoid_shouldThrowSanitizedOutboundExceptionWhenRollbackLegacyCompesate() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_RETRY, "COMPESATE", 0, null));

        assertThatThrownBy(() -> publisher.publishAlwaysFail("legacy-compesate"))
                .isInstanceOf(OutboundException.class)
                .hasNoCause()
                .extracting("errorCode")
                .isEqualTo(OutboundErrorCode.BROKER_UNAVAILABLE);
    }

    @Test
    void publishVoid_shouldWaitForAllCapturedFuturesWhenMethodSendsMultipleRecords() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_MULTI, "IGNORE", 0, null));

        publisher.publishMultiple("first", "second");

        assertThat(listener.pollValue()).isEqualTo("first");
        assertThat(listener.pollValue()).isEqualTo("second");
    }

    @Test
    void publishVoid_shouldRetryWhenOneOfMultipleCapturedBrokerFuturesFails() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_MULTI, "IGNORE", 1, null));

        publisher.publishMultipleCapturedRetryThenSuccess("multi-retry-1", "multi-retry-2");

        assertThat(listener.pollValue()).isEqualTo("multi-retry-1");
        assertThat(listener.pollValue()).isEqualTo("multi-retry-2");
        verify(auditLogger, times(2)).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void publishNoSend_shouldAuditPublishInvokedWarning() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_MULTI_AUDIT, "IGNORE", 0, null));

        publisher.publishNoSend();

        ArgumentCaptor<SecurityResultStatus> statusCaptor = ArgumentCaptor.forClass(SecurityResultStatus.class);
        ArgumentCaptor<SecurityErrorCode> errorCaptor = ArgumentCaptor.forClass(SecurityErrorCode.class);
        verify(auditLogger).logOutbound(any(), any(), statusCaptor.capture(), errorCaptor.capture(), anyLong(), anyInt());
        assertThat(statusCaptor.getValue()).isEqualTo(SecurityResultStatus.WARN);
        assertThat(errorCaptor.getValue()).isEqualTo(SecurityErrorCode.PUBLISH_INVOKED);
    }

    @Test
    void publishVoid_shouldAuditResponseTimeThresholdWarning() {
        when(policyService.resolve(any())).thenReturn(policy(TOPIC_THRESHOLD, "IGNORE", 0, 0));

        publisher.publishSlow("slow-1");

        assertThat(listener.pollValue()).isEqualTo("slow-1");
        ArgumentCaptor<SecurityResultStatus> statusCaptor = ArgumentCaptor.forClass(SecurityResultStatus.class);
        ArgumentCaptor<SecurityErrorCode> errorCaptor = ArgumentCaptor.forClass(SecurityErrorCode.class);
        verify(auditLogger, times(2)).logOutbound(any(), any(), statusCaptor.capture(), errorCaptor.capture(), anyLong(), anyInt());
        assertThat(statusCaptor.getAllValues()).containsExactly(SecurityResultStatus.SUCCESS, SecurityResultStatus.WARN);
        assertThat(errorCaptor.getAllValues()).containsExactly(null, SecurityErrorCode.RESPONSE_TIME_THRESHOLD_EXCEEDED);
    }

    @Test
    void testPublisher_shouldBeSpringAopProxy() {
        assertThat(AopUtils.isAopProxy(publisher)).isTrue();
    }

    private static OutboundExecutionPolicy policy(String topic, String rollbackStrategy, int retryCount,
                                                  Integer responseTimeThresholdMs) {
        return policy(topic, rollbackStrategy, 5_000, retryCount, responseTimeThresholdMs);
    }

    private static OutboundExecutionPolicy policy(String topic, String rollbackStrategy, int timeoutMs, int retryCount,
                                                  Integer responseTimeThresholdMs) {
        return new OutboundExecutionPolicy("endpoint-" + topic, "IT Outbound MQ", "user-service", null, topic,
                "PUB", "MQ", timeoutMs, retryCount, 0, responseTimeThresholdMs, 30, rollbackStrategy, null, null,
                List.of(), new OutboundSettingsDTO());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = SecurityAutoConfiguration.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableKafka
    @Import({OutBoundSecurityAspect.class, KafkaSendCaptureAspect.class, OutboundContextHolder.class})
    static class TestApplication {
        @Bean
        OutboundPolicyService outboundPolicyService() {
            return Mockito.mock(OutboundPolicyService.class);
        }

        @Bean
        SecurityAuditLogger securityAuditLogger() {
            return Mockito.mock(SecurityAuditLogger.class);
        }

        @Bean
        TestMqPublisher testMqPublisher(KafkaTemplate<String, String> kafkaTemplate) {
            return new TestMqPublisher(kafkaTemplate);
        }

        @Bean
        TestMqListener testMqListener() {
            return new TestMqListener();
        }
    }

    static class TestMqListener {
        private final LinkedBlockingQueue<String> values = new LinkedBlockingQueue<>();

        @KafkaListener(
                topics = {TOPIC_VOID, TOPIC_FUTURE, TOPIC_RETRY, TOPIC_MULTI, TOPIC_THRESHOLD},
                groupId = "outbound-mq-it-listener")
        void listen(ConsumerRecord<String, String> record) {
            values.add(record.value());
        }

        String pollValue() {
            try {
                return values.poll(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        void clear() {
            values.clear();
        }
    }

    static class TestMqPublisher {
        private final KafkaTemplate<String, String> kafkaTemplate;
        private final AtomicInteger retryAttempts = new AtomicInteger();
        private final AtomicInteger futureRetryAttempts = new AtomicInteger();
        private final AtomicInteger futureTimeoutAttempts = new AtomicInteger();
        private final AtomicInteger capturedRetryAttempts = new AtomicInteger();
        private final AtomicInteger multiRetryAttempts = new AtomicInteger();

        TestMqPublisher(KafkaTemplate<String, String> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        void reset() {
            retryAttempts.set(0);
            futureRetryAttempts.set(0);
            futureTimeoutAttempts.set(0);
            capturedRetryAttempts.set(0);
            multiRetryAttempts.set(0);
        }

        @OutBoundSecurity(name = "IT Void Publish", topic = TOPIC_VOID, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishVoid(String value) {
            kafkaTemplate.send(TOPIC_VOID, value);
        }

        @OutBoundSecurity(name = "IT Future Publish", topic = TOPIC_FUTURE, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public CompletableFuture<SendResult<String, String>> publishFuture(String value) {
            return kafkaTemplate.send(TOPIC_FUTURE, value);
        }

        @OutBoundSecurity(name = "IT Future Retry Publish", topic = TOPIC_FUTURE, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public CompletableFuture<SendResult<String, String>> publishFutureRetryThenSuccess(String value) {
            if (futureRetryAttempts.getAndIncrement() == 0) {
                return failedFuture(new NetworkException("broker future failed"));
            }
            return kafkaTemplate.send(TOPIC_FUTURE, value);
        }

        @OutBoundSecurity(name = "IT Future Timeout Retry Publish", topic = TOPIC_FUTURE, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public CompletableFuture<SendResult<String, String>> publishFutureTimeoutRetryThenSuccess(String value) {
            if (futureTimeoutAttempts.getAndIncrement() == 0) {
                return new CompletableFuture<>();
            }
            return CompletableFuture.completedFuture(null);
        }

        @OutBoundSecurity(name = "IT Future Non Retryable Publish", topic = TOPIC_FUTURE, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public CompletableFuture<SendResult<String, String>> publishFutureNonRetryableFailure() {
            return failedFuture(new SerializationException("serialization failed"));
        }

        @OutBoundSecurity(name = "IT Retry Publish", topic = TOPIC_RETRY, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishRetryThenSuccess(String value) {
            if (retryAttempts.getAndIncrement() == 0) {
                throw new NetworkException("transient broker failure");
            }
            kafkaTemplate.send(TOPIC_RETRY, value);
        }

        @OutBoundSecurity(name = "IT Captured Future Retry Publish", topic = TOPIC_RETRY, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishCapturedFutureRetryThenSuccess(String value) {
            if (capturedRetryAttempts.getAndIncrement() == 0) {
                KafkaSendCaptureContext.capture(failedFuture(new NetworkException("captured broker future failed")));
                return;
            }
            kafkaTemplate.send(TOPIC_RETRY, value);
        }

        @OutBoundSecurity(name = "IT Always Fail Publish", topic = TOPIC_RETRY, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishAlwaysFail(String value) {
            throw new NetworkException("broker unavailable");
        }

        @OutBoundSecurity(name = "IT Multi Publish", topic = TOPIC_MULTI, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishMultiple(String first, String second) {
            kafkaTemplate.send(TOPIC_MULTI, first);
            kafkaTemplate.send(TOPIC_MULTI, second);
        }

        @OutBoundSecurity(name = "IT Multi Captured Retry Publish", topic = TOPIC_MULTI, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishMultipleCapturedRetryThenSuccess(String first, String second) {
            if (multiRetryAttempts.getAndIncrement() == 0) {
                KafkaSendCaptureContext.capture(CompletableFuture.completedFuture(null));
                KafkaSendCaptureContext.capture(failedFuture(new NetworkException("one captured future failed")));
                return;
            }
            kafkaTemplate.send(TOPIC_MULTI, first);
            kafkaTemplate.send(TOPIC_MULTI, second);
        }

        @OutBoundSecurity(name = "IT No Send Publish", topic = TOPIC_MULTI_AUDIT, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishNoSend() {
        }

        @OutBoundSecurity(name = "IT Slow Publish", topic = TOPIC_THRESHOLD, method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        public void publishSlow(String value) {
            sleep(2);
            kafkaTemplate.send(TOPIC_THRESHOLD, value);
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        private static CompletableFuture<SendResult<String, String>> failedFuture(Throwable failure) {
            CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
            future.completeExceptionally(failure);
            return future;
        }
    }
}
