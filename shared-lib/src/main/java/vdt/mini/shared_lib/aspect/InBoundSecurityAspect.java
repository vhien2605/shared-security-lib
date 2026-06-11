package vdt.mini.shared_lib.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.document.InboundSettingsDTO;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.security.RedisRateLimiter;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.enums.SecurityResultStatus;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class InBoundSecurityAspect {
    private final RedisRateLimiter rateLimiter;
    private final SecurityAuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public InBoundSecurityAspect(RedisRateLimiter rateLimiter, SecurityAuditLogger auditLogger, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @Pointcut("@annotation(vdt.mini.shared_lib.annotation.InBoundSecurity)")
    public void inboundPointcut() {
    }

    @Around("inboundPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        SecurityRequestContext context = SecurityRequestContextHolder.get();
        if (context == null || context.getEndpointId() == null) {
            return joinPoint.proceed();
        }
        boolean mq = "MQ".equalsIgnoreCase(context.getProtocol());
        if (!mq) {
            applyRateLimit(context);
        }
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            context.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            checkResponseSize(context, result);
            if (!mq) {
                logTiming(context);
                auditLogger.log(context, SecurityResultStatus.SUCCESS, null);
            }
            return result;
        } catch (InboundSecurityException ex) {
            context.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            if (!mq) {
                auditLogger.log(context, SecurityResultStatus.DENIED, ex.getErrorCode());
            }
            throw ex;
        } catch (Throwable ex) {
            context.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            if (!mq) {
                auditLogger.log(context, SecurityResultStatus.FAILED, SecurityErrorCode.INTERNAL_ERROR);
            }
            throw ex;
        }
    }

    private void applyRateLimit(SecurityRequestContext context) {
        Integer limit = context.getRateLimit();
        Integer windowSeconds = context.getRateLimitWindowSeconds();
        if (limit == null || windowSeconds == null || limit <= 0 || windowSeconds <= 0) {
            return;
        }
        String subject = context.getClientKey() != null ? context.getClientKey() : context.getSourceIp();
        RedisRateLimiter.RateLimitResult result = rateLimiter.check(
                context.getServiceId(), context.getEndpointId(), subject, limit, windowSeconds);
        context.setRemainingQuota(result.remainingQuota());
        if (!result.allowed()) {
            auditLogger.log(context, SecurityResultStatus.DENIED, SecurityErrorCode.RATE_LIMIT_EXCEEDED);
            throw new InboundSecurityException(SecurityErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded");
        }
    }

    private void logTiming(SecurityRequestContext context) {
        if (context.getTimeoutMs() != null && context.getTimeoutMs() > 0 && context.getDurationMs() > context.getTimeoutMs()) {
            auditLogger.log(context, SecurityResultStatus.TIMEOUT, SecurityErrorCode.TIMEOUT_EXCEEDED);
        } else if (context.getThresholdMs() != null && context.getThresholdMs() > 0 && context.getDurationMs() > context.getThresholdMs()) {
            auditLogger.log(context, SecurityResultStatus.WARN, SecurityErrorCode.RESPONSE_TIME_THRESHOLD_EXCEEDED);
        }
    }

    private void checkResponseSize(SecurityRequestContext context, Object result) {
        InboundSettingsDTO settings = context.getInboundSettings();
        if (settings == null || result == null) {
            return;
        }
        Integer limitKb = settings.getResponseSizeLimitKb();
        if (limitKb == null || limitKb <= 0) {
            return;
        }
        long size = estimateSize(result);
        context.setResponseSizeBytes(size);
        if (size > limitKb * 1024L) {
            throw new InboundSecurityException(SecurityErrorCode.RESPONSE_SIZE_EXCEEDED, "Response size exceeded");
        }
    }

    private long estimateSize(Object value) {
        if (value instanceof ResponseEntity<?> responseEntity) {
            return estimateSize(responseEntity.getBody());
        }
        if (value instanceof byte[] bytes) {
            return bytes.length;
        }
        if (value instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8).length;
        }
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException ex) {
            return 0L;
        }
    }
}
