package vdt.mini.shared_lib.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RedisRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final long TTL_BUFFER_SECONDS = 5;

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(@Qualifier("securityRedisTemplate") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult check(String serviceId, String endpointId, String clientOrIp, int limit, int windowSeconds) {
        if (limit <= 0 || windowSeconds <= 0) {
            return RateLimitResult.allowed(Long.MAX_VALUE, null);
        }
        long windowStart = Instant.now().getEpochSecond() / windowSeconds * windowSeconds;
        String key = "security:runtime:ratelimit:http:in:%s:%s:%s:%d"
                .formatted(safe(serviceId), safe(endpointId), safe(clientOrIp), windowStart);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + TTL_BUFFER_SECONDS));
            }
            long used = count == null ? 0L : count;
            long remaining = Math.max(0L, limit - used);
            return used <= limit ? RateLimitResult.allowed(remaining, key) : RateLimitResult.denied(0L, key);
        } catch (RuntimeException ex) {
            log.warn("Redis rate limit check failed key={}; failing open", key, ex);
            return RateLimitResult.allowed(Long.MAX_VALUE, key);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replace(':', '_');
    }

    public record RateLimitResult(boolean allowed, long remainingQuota, String key) {
        static RateLimitResult allowed(long remainingQuota, String key) {
            return new RateLimitResult(true, remainingQuota, key);
        }

        static RateLimitResult denied(long remainingQuota, String key) {
            return new RateLimitResult(false, remainingQuota, key);
        }
    }
}
