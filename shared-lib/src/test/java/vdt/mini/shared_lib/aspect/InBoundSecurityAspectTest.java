package vdt.mini.shared_lib.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.security.RedisRateLimiter;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InBoundSecurityAspectTest {
    @Mock
    private RedisRateLimiter rateLimiter;
    @Mock
    private SecurityAuditLogger auditLogger;
    @Mock
    private ProceedingJoinPoint joinPoint;

    private InBoundSecurityAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new InBoundSecurityAspect(rateLimiter, auditLogger, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityRequestContextHolder.clear();
    }

    @Test
    void around_shouldDenyHttpResponse_whenResponseSizeExceeded() throws Throwable {
        SecurityRequestContext context = context("HTTP");
        SecurityRequestContextHolder.set(context);
        when(joinPoint.proceed()).thenReturn("x".repeat(2048));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.RESPONSE_SIZE_EXCEEDED));

        assertThat(context.getResponseSizeBytes()).isEqualTo(2048L);
        verify(auditLogger).log(context, SecurityResultStatus.DENIED, SecurityErrorCode.RESPONSE_SIZE_EXCEEDED);
    }

    @Test
    void around_shouldDenyMqResponse_whenResponseSizeExceeded_withoutAspectAudit() throws Throwable {
        SecurityRequestContext context = context("MQ");
        SecurityRequestContextHolder.set(context);
        when(joinPoint.proceed()).thenReturn("x".repeat(2048));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(InboundSecurityException.class)
                .satisfies(ex -> assertThat(((InboundSecurityException) ex).getErrorCode())
                        .isEqualTo(SecurityErrorCode.RESPONSE_SIZE_EXCEEDED));

        assertThat(context.getResponseSizeBytes()).isEqualTo(2048L);
        verify(auditLogger, never()).log(any(SecurityRequestContext.class), any(), any());
    }

    @Test
    void around_shouldAllowMqResponseWithinLimit() throws Throwable {
        SecurityRequestContext context = context("MQ");
        SecurityRequestContextHolder.set(context);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(context.getResponseSizeBytes()).isEqualTo(2L);
        verify(auditLogger, never()).log(any(SecurityRequestContext.class), any(), any());
    }

    private SecurityRequestContext context(String protocol) {
        SecurityRequestContext context = new SecurityRequestContext();
        context.setServiceId("service-1");
        context.setEndpointId("endpoint-1");
        context.setProtocol(protocol);
        context.setClientKey("client-key");
        context.setInboundSettings(new InboundSettingsDTO("endpoint-1", "Endpoint", "/orders", "topic", "POST", protocol, true,
                "ACTIVE", "ACTIVE", true, 5, 60, 30000, 1, 1, 5000, 30, null, null, null,
                List.of(), List.of(), List.of()));
        return context;
    }
}
