package vdt.mini.shared_lib.aspect;

import feign.FeignException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import vdt.mini.shared_lib.annotation.OutBoundSecurity;
import vdt.mini.shared_lib.enums.OutboundErrorCode;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.enums.SecurityResultStatus;
import vdt.mini.shared_lib.exception.OutboundException;
import vdt.mini.shared_lib.web.OutboundContext;
import vdt.mini.shared_lib.web.OutboundContextHolder;
import vdt.mini.shared_lib.web.OutboundExecutionPolicy;
import vdt.mini.shared_lib.web.OutboundPolicyService;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.security.SecurityAuditLogger;

import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Aspect
@Component
public class OutBoundSecurityAspect {
    private static final Logger log = LoggerFactory.getLogger(OutBoundSecurityAspect.class);

    private final OutboundPolicyService policyService;
    private final OutboundContextHolder contextHolder;
    private final SecurityAuditLogger auditLogger;

    public OutBoundSecurityAspect(OutboundPolicyService policyService, OutboundContextHolder contextHolder, SecurityAuditLogger auditLogger) {
        this.policyService = policyService;
        this.contextHolder = contextHolder;
        this.auditLogger = auditLogger;
    }

    @Pointcut("@annotation(vdt.mini.shared_lib.annotation.OutBoundSecurity)")
    public void outboundPointcut() {
    }

    @Around("outboundPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        OutBoundSecurity annotation = resolveAnnotation(joinPoint);
        OutboundExecutionPolicy policy = policyService.resolve(annotation);
        OutboundContext outboundContext = buildContext(policy);
        contextHolder.set(outboundContext);
        try {
            return proceedWithRetry(joinPoint, policy, outboundContext);
        } finally {
            contextHolder.clear();
        }
    }

    private Object proceedWithRetry(ProceedingJoinPoint joinPoint, OutboundExecutionPolicy policy,
                                    OutboundContext outboundContext) throws Throwable {
        Throwable lastFailure = null;
        OutboundErrorCode lastErrorCode = null;
        boolean retried = false;
        for (int attempt = 0; attempt <= policy.retryCount(); attempt++) {
            long startedAt = System.nanoTime();
            try {
                Object result = joinPoint.proceed();
                long durationMs = elapsedMs(startedAt);
                log.info("outbound_http_success endpointId={} attempt={} durationMs={}", policy.endpointId(), attempt, durationMs);
                auditLogger.logOutbound(policy, outboundContext, SecurityResultStatus.SUCCESS, null, durationMs, attempt);
                if (policy.responseTimeThresholdMs() != null && durationMs > policy.responseTimeThresholdMs()) {
                    log.warn("outbound_http_response_time_threshold_exceeded endpointId={} durationMs={} thresholdMs={}",
                            policy.endpointId(), durationMs, policy.responseTimeThresholdMs());
                    auditLogger.logOutbound(policy, outboundContext, SecurityResultStatus.WARN,
                            SecurityErrorCode.RESPONSE_TIME_THRESHOLD_EXCEEDED, durationMs, attempt);
                }
                return result;
            } catch (Throwable failure) {
                lastFailure = failure;
                lastErrorCode = classify(failure, false);
                long durationMs = elapsedMs(startedAt);
                if (!isRetryable(failure) || attempt >= policy.retryCount()) {
                    OutboundErrorCode finalCode = retried ? OutboundErrorCode.RETRY_EXHAUSTED : lastErrorCode;
                    logFailure(policy, attempt, durationMs, failure, finalCode);
                    auditLogger.logOutbound(policy, outboundContext,
                            finalCode == OutboundErrorCode.TIMEOUT_EXCEEDED ? SecurityResultStatus.TIMEOUT : SecurityResultStatus.FAILED,
                            toSecurityErrorCode(finalCode), durationMs, attempt);
                    return handleRollbackStrategy(policy, finalCode, failure);
                }
                retried = true;
                log.warn("outbound_http_retry endpointId={} attempt={} nextAttempt={} errorCode={} backoffMs={}",
                        policy.endpointId(), attempt, attempt + 1, lastErrorCode, policy.retryBackoffMs(), failure);
                auditLogger.logOutbound(policy, outboundContext, SecurityResultStatus.RETRY,
                        toSecurityErrorCode(lastErrorCode), durationMs, attempt);
                sleep(policy.retryBackoffMs());
            }
        }
        throw new OutboundException(lastErrorCode == null ? OutboundErrorCode.INTERNAL_ERROR : lastErrorCode,
                "Outbound call failed", lastFailure, policy.endpointId());
    }

    private void logFailure(OutboundExecutionPolicy policy, int attempt, long durationMs, Throwable failure, OutboundErrorCode errorCode) {
        if (errorCode == OutboundErrorCode.TIMEOUT_EXCEEDED) {
            log.warn("outbound_http_timeout endpointId={} attempt={} durationMs={} timeoutMs={}",
                    policy.endpointId(), attempt, durationMs, policy.timeoutMs(), failure);
        }
        if (errorCode == OutboundErrorCode.RETRY_EXHAUSTED) {
            log.warn("outbound_http_retry_exhausted endpointId={} attempts={} durationMs={} causeCode={}",
                    policy.endpointId(), attempt + 1, durationMs, classify(failure, false), failure);
        } else {
            log.warn("outbound_http_failed endpointId={} attempt={} durationMs={} errorCode={}",
                    policy.endpointId(), attempt, durationMs, errorCode, failure);
        }
    }

    private Object handleRollbackStrategy(OutboundExecutionPolicy policy, OutboundErrorCode errorCode, Throwable failure) {
        if ("COMPENSATE".equalsIgnoreCase(policy.rollbackStrategy())) {
            throw new OutboundException(errorCode, "Outbound call failed and requires compensation", null, policy.endpointId());
        }
        log.warn("outbound_http_failure_consumed endpointId={} rollbackStrategy={} errorCode={}",
                policy.endpointId(), policy.rollbackStrategy(), errorCode, failure);
        return null;
    }

    private static boolean isRetryable(Throwable failure) {
        if (failure instanceof OutboundException) {
            return false;
        }
        return switch (classify(failure, false)) {
            case HTTP_4XX, HTTP_5XX, HTTP_CLIENT_FAILED, TIMEOUT_EXCEEDED -> true;
            default -> false;
        };
    }

    private static OutboundErrorCode classify(Throwable failure, boolean retryExhausted) {
        if (retryExhausted) {
            return OutboundErrorCode.RETRY_EXHAUSTED;
        }
        if (containsTimeout(failure)) {
            return OutboundErrorCode.TIMEOUT_EXCEEDED;
        }
        Integer status = httpStatus(failure);
        if (status != null) {
            if (status >= 400 && status < 500) {
                return OutboundErrorCode.HTTP_4XX;
            }
            if (status >= 500 && status < 600) {
                return OutboundErrorCode.HTTP_5XX;
            }
        }
        return OutboundErrorCode.HTTP_CLIENT_FAILED;
    }

    private static Integer httpStatus(Throwable failure) {
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor instanceof FeignException feignException) {
                return feignException.status();
            }
            if (cursor instanceof HttpStatusCodeException statusCodeException) {
                return statusCodeException.getStatusCode().value();
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    private static boolean containsTimeout(Throwable failure) {
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor instanceof TimeoutException || cursor instanceof SocketTimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private OutBoundSecurity resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(OutBoundSecurity.class);
    }

    private OutboundContext buildContext(OutboundExecutionPolicy policy) {
        SecurityRequestContext inboundContext = SecurityRequestContextHolder.get();
        String traceId = firstNonBlank(inboundContext == null ? null : inboundContext.getTraceId(), MDC.get("traceId"));
        traceId = firstNonBlank(traceId, UUID.randomUUID().toString());
        String correlationId = firstNonBlank(inboundContext == null ? null : inboundContext.getCorrelationId(), MDC.get("correlationId"));
        correlationId = firstNonBlank(correlationId, traceId);
        return new OutboundContext(policy.serviceId(), policy.endpointId(), policy.endpointName(), policy.targetUrl(),
                policy.method(), policy.protocol(), traceId, correlationId, Instant.now(), UUID.randomUUID().toString());
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static void sleep(int backoffMs) throws InterruptedException {
        if (backoffMs > 0) {
            Thread.sleep(backoffMs);
        }
    }

    private static SecurityErrorCode toSecurityErrorCode(OutboundErrorCode outboundErrorCode) {
        if (outboundErrorCode == null) {
            return SecurityErrorCode.INTERNAL_ERROR;
        }
        return switch (outboundErrorCode) {
            case ENDPOINT_NOT_REGISTERED -> SecurityErrorCode.ENDPOINT_NOT_REGISTERED;
            case ENDPOINT_DISABLED -> SecurityErrorCode.ENDPOINT_DISABLED;
            case ENDPOINT_INACTIVE -> SecurityErrorCode.ENDPOINT_INACTIVE;
            case HTTP_4XX -> SecurityErrorCode.HTTP_4XX;
            case HTTP_5XX -> SecurityErrorCode.HTTP_5XX;
            case HTTP_CLIENT_FAILED -> SecurityErrorCode.HTTP_CLIENT_FAILED;
            case TIMEOUT_EXCEEDED -> SecurityErrorCode.TIMEOUT_EXCEEDED;
            case RETRY_EXHAUSTED -> SecurityErrorCode.RETRY_EXHAUSTED;
            case INVALID_REQUEST -> SecurityErrorCode.INVALID_REQUEST;
            case INTERNAL_ERROR -> SecurityErrorCode.INTERNAL_ERROR;
        };
    }
}
