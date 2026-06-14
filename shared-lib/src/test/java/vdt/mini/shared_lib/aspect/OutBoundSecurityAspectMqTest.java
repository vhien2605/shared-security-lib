package vdt.mini.shared_lib.aspect;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.NetworkException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.SendResult;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.shared_lib.enums.OutboundErrorCode;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.exception.OutboundException;
import vdt.mini.shared_lib.mq.KafkaSendCaptureContext;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.web.OutboundContextHolder;
import vdt.mini.shared_lib.web.OutboundExecutionPolicy;
import vdt.mini.shared_lib.web.OutboundPolicyService;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutBoundSecurityAspectMqTest {
    private OutboundPolicyService policyService;
    private OutboundContextHolder contextHolder;
    private SecurityAuditLogger auditLogger;
    private OutBoundSecurityAspect aspect;

    @BeforeEach
    void setUp() {
        policyService = mock(OutboundPolicyService.class);
        contextHolder = new OutboundContextHolder();
        auditLogger = mock(SecurityAuditLogger.class);
        aspect = new OutBoundSecurityAspect(policyService, contextHolder, auditLogger);
        SecurityRequestContextHolder.clear();
    }

    @Test
    void around_shouldReturnSendResultAndClearContext_whenMqPublishAlreadyCompleted() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        SendResult<?, ?> sendResult = mock(SendResult.class);
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        when(joinPoint.proceed()).thenReturn(sendResult);

        Object result = aspect.around(joinPoint);

        assertThat(result).isSameAs(sendResult);
        assertThat(contextHolder.get()).isNull();
        verify(auditLogger).logOutbound(any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void around_shouldWaitForCompletableFutureBrokerAck() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        CompletableFuture<SendResult<?, ?>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        when(joinPoint.proceed()).thenReturn(future);

        Object result = aspect.around(joinPoint);

        assertThat(result).isSameAs(future);
        verify(joinPoint).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldLogPublishInvokedLimitationForVoidFireAndForget() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.around(joinPoint);

        assertThat(result).isNull();
        verify(joinPoint).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldWaitCapturedBrokerAck_whenVoidPublisherCallsKafkaTemplateSend() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        CompletableFuture<SendResult<?, ?>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        doAnswer(invocation -> {
            KafkaSendCaptureContext.capture(future);
            return null;
        }).when(joinPoint).proceed();

        Object result = aspect.around(joinPoint);

        assertThat(result).isNull();
        verify(joinPoint).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldRetryCapturedBrokerAckFailure_whenVoidPublisherCallsKafkaTemplateSend() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        CompletableFuture<SendResult<?, ?>> failedFuture = failedFuture(new NetworkException("broker unavailable"));
        CompletableFuture<SendResult<?, ?>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        doAnswer(invocation -> {
            KafkaSendCaptureContext.capture(failedFuture);
            return null;
        }).doAnswer(invocation -> {
            KafkaSendCaptureContext.capture(successFuture);
            return null;
        }).when(joinPoint).proceed();

        Object result = aspect.around(joinPoint);

        assertThat(result).isNull();
        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldRetryRetriableKafkaAndNetworkFailures() throws Throwable {
        assertRetriesThenSucceeds(new NetworkException("broker unavailable"));
        assertRetriesThenSucceeds(new KafkaException(new NetworkException("transient network")));
    }

    @Test
    void around_shouldRetryTimeoutWaitingBrokerAck() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        when(joinPoint.proceed())
                .thenReturn(failedFuture(new TimeoutException("ack timeout")))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        assertThat(aspect.around(joinPoint)).isInstanceOf(CompletableFuture.class);
        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldRetryExecutionExceptionWithRetryableCause() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        when(joinPoint.proceed())
                .thenReturn(failedFuture(new ExecutionException(new NetworkException("leader unavailable"))))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        aspect.around(joinPoint);

        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldReturnNullAndAuditFailure_whenRetryExhaustedWithIgnore() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        when(joinPoint.proceed()).thenThrow(new NetworkException("broker unavailable"));

        assertThat(aspect.around(joinPoint)).isNull();

        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldReturnNullWithoutRetry_whenExecutionExceptionWrapsNonRetryableFailure() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 3));
        when(joinPoint.proceed()).thenReturn(failedFuture(new ExecutionException(new SerializationException("bad value"))));

        assertThat(aspect.around(joinPoint)).isNull();

        verify(joinPoint, times(1)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldFailVoidPublish_whenAnyCapturedFutureFails() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        CompletableFuture<SendResult<?, ?>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
        CompletableFuture<SendResult<?, ?>> failedFuture = failedFuture(new NetworkException("broker unavailable"));
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        doAnswer(invocation -> {
            KafkaSendCaptureContext.capture(successFuture);
            KafkaSendCaptureContext.capture(failedFuture);
            return null;
        }).when(joinPoint).proceed();

        assertThat(aspect.around(joinPoint)).isNull();

        verify(joinPoint).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldThrowProducerException_whenRollbackCompensateAndGenericKafkaException() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("COMPENSATE", 3));
        when(joinPoint.proceed()).thenThrow(new KafkaException("generic producer failure"));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(OutboundException.class)
                .extracting("errorCode")
                .isEqualTo(OutboundErrorCode.PRODUCER_EXCEPTION);
        verify(joinPoint, times(1)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldThrowPublishFailed_whenRollbackCompensateAndUnknownException() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("COMPENSATE", 3));
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("bad input"));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(OutboundException.class)
                .extracting("errorCode")
                .isEqualTo(OutboundErrorCode.PUBLISH_FAILED);
        verify(joinPoint, times(1)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldNotRetrySerializationAuthorizationInvalidTopicRecordTooLargeOrConfigErrors() throws Throwable {
        assertNonRetryable(new SerializationException("bad value"));
        assertNonRetryable(new AuthorizationException("denied"));
        assertNonRetryable(new InvalidTopicException("bad topic"));
        assertNonRetryable(new RecordTooLargeException("too large"));
        assertNonRetryable(new ConfigException("bad config"));
    }

    @Test
    void around_shouldThrowSanitizedOutboundException_whenRollbackCompensateOrLegacyCompesate() throws Throwable {
        assertCompensates("COMPENSATE");
        assertCompensates("COMPESATE");
    }

    @Test
    void around_shouldAuditResponseTimeThresholdWarning_whenMqPublishExceedsThreshold() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0, 0));
        doAnswer(invocation -> {
            Thread.sleep(2);
            return mock(SendResult.class);
        }).when(joinPoint).proceed();

        aspect.around(joinPoint);

        ArgumentCaptor<SecurityResultStatus> statusCaptor = ArgumentCaptor.forClass(SecurityResultStatus.class);
        ArgumentCaptor<SecurityErrorCode> errorCaptor = ArgumentCaptor.forClass(SecurityErrorCode.class);
        verify(auditLogger, times(2)).logOutbound(any(), any(), statusCaptor.capture(), errorCaptor.capture(), anyLong(), anyInt());
        assertThat(statusCaptor.getAllValues()).containsExactly(SecurityResultStatus.SUCCESS, SecurityResultStatus.WARN);
        assertThat(errorCaptor.getAllValues()).containsExactly(null, SecurityErrorCode.RESPONSE_TIME_THRESHOLD_EXCEEDED);
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldGenerateFallbackTraceAndCorrelationForMqContext_whenMissing() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        when(joinPoint.proceed()).thenReturn(mock(SendResult.class));

        aspect.around(joinPoint);

        ArgumentCaptor<OutboundContext> ctxCaptor = ArgumentCaptor.forClass(OutboundContext.class);
        verify(auditLogger).logOutbound(any(), ctxCaptor.capture(), any(), any(), anyLong(), anyInt());
        OutboundContext ctx = ctxCaptor.getValue();
        assertThat(ctx.traceId()).isNotBlank();
        assertThat(ctx.correlationId()).isNotBlank();
        assertThat(ctx.correlationId()).isEqualTo(ctx.traceId());
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldClearContext_whenMqPolicyFailsBeforeProceed() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenThrow(new OutboundException(OutboundErrorCode.ENDPOINT_DISABLED, "disabled"));

        assertThatThrownBy(() -> aspect.around(joinPoint)).isInstanceOf(OutboundException.class);
        verify(joinPoint, times(0)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    private void assertRetriesThenSucceeds(Throwable failure) throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        when(joinPoint.proceed()).thenThrow(failure).thenReturn(mock(SendResult.class));

        assertThat(aspect.around(joinPoint)).isInstanceOf(SendResult.class);
        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    private void assertNonRetryable(Throwable failure) throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 3));
        when(joinPoint.proceed()).thenThrow(failure);

        assertThat(aspect.around(joinPoint)).isNull();
        verify(joinPoint, times(1)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    private void assertCompensates(String rollbackStrategy) throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("publish");
        NetworkException rawFailure = new NetworkException("broker unavailable with vendor details");
        when(policyService.resolve(any())).thenReturn(policy(rollbackStrategy, 0));
        when(joinPoint.proceed()).thenThrow(rawFailure);

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(OutboundException.class)
                .isNotSameAs(rawFailure)
                .hasNoCause()
                .extracting("errorCode")
                .isEqualTo(OutboundErrorCode.BROKER_UNAVAILABLE);
        assertThat(contextHolder.get()).isNull();
    }

    private static CompletableFuture<SendResult<?, ?>> failedFuture(Throwable failure) {
        CompletableFuture<SendResult<?, ?>> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }

    private ProceedingJoinPoint joinPoint(String methodName) throws Exception {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = SampleMqOutbound.class.getDeclaredMethod(methodName);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    private OutboundExecutionPolicy policy(String rollbackStrategy, int retryCount) {
        return policy(rollbackStrategy, retryCount, null);
    }

    private OutboundExecutionPolicy policy(String rollbackStrategy, int retryCount, Integer responseTimeThresholdMs) {
        return new OutboundExecutionPolicy("mq-endpoint", "User Created", "service-1", "user-service", null, "user.created",
                "PUB", "MQ", 5, retryCount, 0, responseTimeThresholdMs, 30, rollbackStrategy, null, null,
                List.of(), new OutboundSettingsDTO());
    }

    static class SampleMqOutbound {
        @OutBoundSecurity(name = "User Created", topic = "user.created", method = EndpointMethod.PUB, protocol = EndpointProtocol.MQ)
        void publish() {
        }
    }
}
