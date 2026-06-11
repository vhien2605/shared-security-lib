package vdt.mini.shared_lib.mq;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vdt.mini.shared_lib.document.InboundEndpointDTO;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.security.InboundSecurityDecisionService;
import vdt.mini.shared_lib.security.RedisRateLimiter;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityDecision;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.service.EndpointRegistry;
import vdt.mini.shared_lib.service.IdentityManager;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityRecordInterceptorTest {
    private static final String SERVICE_ID = "service-1";
    private static final String ENDPOINT_ID = "endpoint-1";
    private static final String TOPIC = "user.created";
    private static final String CLIENT_KEY = "client-key-1";

    @Mock
    private InboundSecurityDecisionService decisionService;
    @Mock
    private RedisRateLimiter rateLimiter;
    @Mock
    private SecurityAuditLogger auditLogger;
    @Mock
    private IdentityManager identityManager;
    @Mock
    private Consumer<String, Object> consumer;

    private EndpointRegistry endpointRegistry;
    private SecurityRecordInterceptor interceptor;

    @BeforeEach
    void setUp() {
        endpointRegistry = new EndpointRegistry();
        endpointRegistry.replaceAll(List.of(new InboundEndpointDTO(ENDPOINT_ID, "User Created", null, TOPIC, null, "MQ", "", true)), List.of());
        when(identityManager.getOrCreateServiceId()).thenReturn(SERVICE_ID);
        interceptor = new SecurityRecordInterceptor(endpointRegistry, decisionService, new MqSecurityHeaderExtractor(),
                rateLimiter, auditLogger, identityManager, "user-service");
    }

    @AfterEach
    void tearDown() {
        SecurityRequestContextHolder.clear();
    }

    @Test
    void intercept_shouldSkipUnregisteredTopic() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("other.topic", 0, 0L, "key", "payload");

        ConsumerRecord<String, Object> result = interceptor.intercept(record, consumer);

        assertThat(result).isSameAs(record);
        verify(decisionService, never()).decide(any(MqSecurityRequest.class), any(), any(SecurityRequestContext.class));
    }

    @Test
    void intercept_shouldSetContextAndAllowRegisteredRecord() {
        when(decisionService.decide(any(MqSecurityRequest.class), any(), any(SecurityRequestContext.class)))
                .thenReturn(SecurityDecision.allow(ENDPOINT_ID, "client-1", CLIENT_KEY));
        ConsumerRecord<String, Object> record = record("payload");

        ConsumerRecord<String, Object> result = interceptor.intercept(record, consumer);

        assertThat(result).isSameAs(record);
        SecurityRequestContext context = SecurityRequestContextHolder.get();
        assertThat(context).isNotNull();
        assertThat(context.getServiceId()).isEqualTo(SERVICE_ID);
        assertThat(context.getEndpointId()).isEqualTo(ENDPOINT_ID);
        assertThat(context.getTopic()).isEqualTo(TOPIC);
        assertThat(context.getRequestSizeBytes()).isEqualTo("keypayload".getBytes(StandardCharsets.UTF_8).length);

        var endpointCaptor = forClass(EndpointRegistry.InboundMqEndpoint.class);
        verify(decisionService).decide(any(MqSecurityRequest.class), endpointCaptor.capture(), any(SecurityRequestContext.class));
        assertThat(endpointCaptor.getValue().endpointId()).isEqualTo(ENDPOINT_ID);
    }

    @Test
    void intercept_shouldDenyAndLogDecision() {
        when(decisionService.decide(any(MqSecurityRequest.class), any(), any(SecurityRequestContext.class)))
                .thenReturn(SecurityDecision.deny(SecurityResultStatus.DENIED, SecurityErrorCode.API_KEY_INVALID,
                        "Invalid API key", ENDPOINT_ID, null, CLIENT_KEY));

        assertThatThrownBy(() -> interceptor.intercept(record("payload"), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .hasMessage("Invalid API key");

        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.DENIED), eq(SecurityErrorCode.API_KEY_INVALID));
    }

    @Test
    void intercept_shouldDenyWhenRateLimitExceeded() {
        when(decisionService.decide(any(MqSecurityRequest.class), any(), any(SecurityRequestContext.class)))
                .thenAnswer(invocation -> {
                    SecurityRequestContext context = invocation.getArgument(2);
                    context.setRateLimit(1);
                    context.setRateLimitWindowSeconds(60);
                    return SecurityDecision.allow(ENDPOINT_ID, "client-1", CLIENT_KEY);
                });
        when(rateLimiter.checkMqInbound(SERVICE_ID, ENDPOINT_ID, CLIENT_KEY, 1, 60))
                .thenReturn(new RedisRateLimiter.RateLimitResult(false, 0L, "key"));

        assertThatThrownBy(() -> interceptor.intercept(record("payload"), consumer))
                .isInstanceOf(InboundSecurityException.class)
                .hasMessage("Rate limit exceeded");

        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.DENIED), eq(SecurityErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @Test
    void successAndFailure_shouldLogMqOutcomesAndClearContext() {
        when(decisionService.decide(any(MqSecurityRequest.class), any(), any(SecurityRequestContext.class)))
                .thenReturn(SecurityDecision.allow(ENDPOINT_ID, "client-1", CLIENT_KEY));
        ConsumerRecord<String, Object> record = record("payload");

        interceptor.intercept(record, consumer);
        interceptor.success(record, consumer);
        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.SUCCESS), eq(null));

        interceptor.failure(record, new RuntimeException("boom"), consumer);
        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.FAILED), eq(SecurityErrorCode.CONSUME_FAILED));

        interceptor.afterRecord(record, consumer);
        assertThat(SecurityRequestContextHolder.get()).isNull();
    }

    @Test
    void success_shouldLogTimeoutAndSuccess_whenMqDurationExceedsTimeout() throws InterruptedException {
        when(decisionService.decide(any(MqSecurityRequest.class), any(), any(SecurityRequestContext.class)))
                .thenAnswer(invocation -> {
                    SecurityRequestContext context = invocation.getArgument(2);
                    context.setTimeoutMs(1);
                    context.setThresholdMs(1_000);
                    return SecurityDecision.allow(ENDPOINT_ID, "client-1", CLIENT_KEY);
                });
        ConsumerRecord<String, Object> record = record("payload");

        interceptor.intercept(record, consumer);
        Thread.sleep(20);
        interceptor.success(record, consumer);

        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.TIMEOUT), eq(SecurityErrorCode.TIMEOUT_EXCEEDED));
        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.SUCCESS), eq(null));
    }

    @Test
    void success_shouldLogWarningAndSuccess_whenMqDurationExceedsThreshold() throws InterruptedException {
        when(decisionService.decide(any(MqSecurityRequest.class), any(), any(SecurityRequestContext.class)))
                .thenAnswer(invocation -> {
                    SecurityRequestContext context = invocation.getArgument(2);
                    context.setTimeoutMs(1_000);
                    context.setThresholdMs(1);
                    return SecurityDecision.allow(ENDPOINT_ID, "client-1", CLIENT_KEY);
                });
        ConsumerRecord<String, Object> record = record("payload");

        interceptor.intercept(record, consumer);
        Thread.sleep(20);
        interceptor.success(record, consumer);

        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.WARN), eq(SecurityErrorCode.RESPONSE_TIME_THRESHOLD_EXCEEDED));
        verify(auditLogger).log(any(SecurityRequestContext.class), eq(SecurityResultStatus.SUCCESS), eq(null));
    }

    private ConsumerRecord<String, Object> record(Object value) {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(TOPIC, 0, 0L, "key", value);
        record.headers().add(InboundSecurityDecisionService.CLIENT_KEY_HEADER, CLIENT_KEY.getBytes(StandardCharsets.UTF_8));
        record.headers().add(MqSecurityHeaderExtractor.CORRELATION_ID_HEADER, "corr".getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
