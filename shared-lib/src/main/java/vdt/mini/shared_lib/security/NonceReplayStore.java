package vdt.mini.shared_lib.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class NonceReplayStore {
    private static final Logger log = LoggerFactory.getLogger(NonceReplayStore.class);
    private final StringRedisTemplate redisTemplate;

    public NonceReplayStore(@Qualifier("securityRedisTemplate") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean seenOrStore(String serviceId, String endpointId, String clientKey, String nonce, Duration ttl) {
        String key = "security:runtime:nonce:http:in:%s:%s:%s:%s"
                .formatted(safe(serviceId), safe(endpointId), safe(clientKey), safe(nonce));
        try {
            Boolean stored = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return !Boolean.TRUE.equals(stored);
        } catch (RuntimeException ex) {
            log.warn("Redis nonce replay check failed key={}; failing closed for HMAC", key, ex);
            return true;
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replace(':', '_');
    }
}
