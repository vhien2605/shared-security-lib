package vdt.mini.shared_lib.aspect;

import feign.FeignException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.document.OutboundSettingsDTO;
import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;
import vdt.mini.shared_lib.enums.OutboundErrorCode;
import vdt.mini.shared_lib.exception.OutboundException;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;
import vdt.mini.shared_lib.web.OutboundExecutionPolicy;
import vdt.mini.shared_lib.web.OutboundPolicyService;
import vdt.mini.shared_lib.security.SecurityAuditLogger;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutBoundSecurityAspectTest {
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
    }

    @AfterEach
    void tearDown() {
        SecurityRequestContextHolder.clear();
    }

    @Test
    void around_shouldReturnResultAndClearContext_whenSuccess() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        SecurityRequestContext inboundContext = new SecurityRequestContext();
        inboundContext.setTraceId("trace-1");
        inboundContext.setCorrelationId("corr-1");
        SecurityRequestContextHolder.set(inboundContext);
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(contextHolder.get()).isNull();
        ArgumentCaptor<OutboundContext> outboundContext = ArgumentCaptor.forClass(OutboundContext.class);
        verify(auditLogger).logOutbound(any(), outboundContext.capture(), any(), any(), anyLong(), anyInt());
        assertThat(outboundContext.getValue().traceId()).isEqualTo("trace-1");
        assertThat(outboundContext.getValue().correlationId()).isEqualTo("corr-1");
    }

    @Test
    void around_shouldGenerateTraceAndCorrelationIds_whenNoInboundContextExists() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<OutboundContext> outboundContext = ArgumentCaptor.forClass(OutboundContext.class);
        verify(auditLogger).logOutbound(any(), outboundContext.capture(), any(), any(), anyLong(), anyInt());
        assertThat(outboundContext.getValue().traceId()).isNotBlank();
        assertThat(outboundContext.getValue().correlationId()).isEqualTo(outboundContext.getValue().traceId());
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldRetryHttp4xxAccordingToPolicy_thenReturnSuccess() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        when(joinPoint.proceed())
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found", null, null, null))
                .thenReturn("ok");

        assertThat(aspect.around(joinPoint)).isEqualTo("ok");

        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldRetryHttp5xxNetworkAndTimeout_thenConsumeWhenRollbackIgnore() throws Throwable {
        assertRetriesAndConsumes(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "bad gateway", null, null, null));
        assertRetriesAndConsumes(new IllegalStateException("network down"));
        assertRetriesAndConsumes(new RuntimeException(new SocketTimeoutException("timeout")));
    }

    @Test
    void around_shouldConsumeFeignFailure_whenRollbackIgnore() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        FeignException failure = mockFeignException(400);
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 0));
        when(joinPoint.proceed()).thenThrow(failure);

        assertThat(aspect.around(joinPoint)).isNull();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldRetryFeign4xx_thenConsumeWhenRollbackIgnore() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        FeignException failure = mockFeignException(400);
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        when(joinPoint.proceed()).thenThrow(failure);

        assertThat(aspect.around(joinPoint)).isNull();

        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldThrowOutboundException_whenRollbackCompensate() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        when(policyService.resolve(any())).thenReturn(policy("COMPENSATE", 1));
        when(joinPoint.proceed()).thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "conflict", null, null, null));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(OutboundException.class)
                .hasNoCause()
                .extracting("errorCode")
                .isEqualTo(OutboundErrorCode.RETRY_EXHAUSTED);
        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    @Test
    void around_shouldClearContext_whenNonRetryablePolicyFailureOccursBeforeProceed() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        when(policyService.resolve(any())).thenThrow(new OutboundException(OutboundErrorCode.ENDPOINT_DISABLED, "disabled"));

        assertThatThrownBy(() -> aspect.around(joinPoint)).isInstanceOf(OutboundException.class);
        assertThat(contextHolder.get()).isNull();
        verify(joinPoint, times(0)).proceed();
    }

    private void assertRetriesAndConsumes(Throwable failure) throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint();
        when(policyService.resolve(any())).thenReturn(policy("IGNORE", 1));
        when(joinPoint.proceed()).thenThrow(failure);

        assertThat(aspect.around(joinPoint)).isNull();
        verify(joinPoint, times(2)).proceed();
        assertThat(contextHolder.get()).isNull();
    }

    private FeignException mockFeignException(int status) {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(status);
        return exception;
    }

    private ProceedingJoinPoint joinPoint() throws Exception {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = SampleOutbound.class.getDeclaredMethod("call");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    private OutboundExecutionPolicy policy(String rollbackStrategy, int retryCount) {
        return new OutboundExecutionPolicy("endpoint-1", "Profile API", "service-1", "http://profile/users",
                "GET", "HTTP", 1000, retryCount, 0, null, 30, rollbackStrategy, null, null,
                java.util.List.of(), new OutboundSettingsDTO());
    }

    static class SampleOutbound {
        @OutBoundSecurity(name = "Profile API", targetUrl = "http://profile/users", method = EndpointMethod.GET, protocol = EndpointProtocol.HTTP)
        void call() {
        }
    }
}
