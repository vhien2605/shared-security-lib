package vdt.mini.shared_lib.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import vdt.mini.shared_lib.exception.InboundSecurityException;
import vdt.mini.shared_lib.security.RedisRateLimiter;
import vdt.mini.shared_lib.security.SecurityAuditLogger;
import vdt.mini.shared_lib.enums.SecurityErrorCode;
import vdt.mini.shared_lib.security.SecurityRequestContext;
import vdt.mini.shared_lib.security.SecurityRequestContextHolder;
import vdt.mini.shared_lib.enums.SecurityResultStatus;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class InBoundSecurityAspect {
    private final RedisRateLimiter rateLimiter;
    private final SecurityAuditLogger auditLogger;

    public InBoundSecurityAspect(RedisRateLimiter rateLimiter, SecurityAuditLogger auditLogger) {
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
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
        applyRateLimit(context);
        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            context.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            logTiming(context);
            auditLogger.log(context, SecurityResultStatus.SUCCESS, null);
            return result;
        } catch (Throwable ex) {
            context.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            auditLogger.log(context, SecurityResultStatus.FAILED, SecurityErrorCode.INTERNAL_ERROR);
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
}
