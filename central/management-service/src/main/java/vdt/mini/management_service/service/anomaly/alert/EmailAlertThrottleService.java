package vdt.mini.management_service.service.anomaly.alert;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class EmailAlertThrottleService {
    private static final String KEY_PREFIX = "security:anomaly:alert:email:";
    private final StringRedisTemplate redisTemplate;

    public EmailAlertThrottleService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(String incidentId, Integer throttleMinutes) {
        String safeIncidentId = incidentId == null || incidentId.isBlank() ? "unknown" : incidentId;
        int minutes = throttleMinutes == null || throttleMinutes < 1 ? 1 : throttleMinutes;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + safeIncidentId, "1", Duration.ofMinutes(minutes));
        return Boolean.TRUE.equals(acquired);
    }
}
