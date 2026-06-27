package vdt.mini.management_service.service.anomaly.rolling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vdt.mini.management_service.config.AnomalyDetectionProperties;
import vdt.mini.management_service.dto.event.AnomalyGroupKey;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.dto.event.RollingWindowSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RedisRollingWindowStore implements RollingWindowStore {
    private static final Logger log = LoggerFactory.getLogger(RedisRollingWindowStore.class);
    private static final String KEY_PREFIX = "security:anomaly:rolling:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AnomalyDetectionProperties properties;
    private final RollingWindowSnapshotCalculator snapshotCalculator;

    public RedisRollingWindowStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                   AnomalyDetectionProperties properties,
                                   RollingWindowSnapshotCalculator snapshotCalculator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.snapshotCalculator = snapshotCalculator;
    }

    @Override
    public RollingWindowSnapshot snapshotBefore(AnomalyGroupKey key, Instant currentTimestamp) {
        Instant windowStart = currentTimestamp.minus(properties.getRolling().getWindowSize());
        Set<String> members = redisTemplate.opsForZSet().rangeByScore(redisKey(key), windowStart.toEpochMilli(), currentTimestamp.toEpochMilli() - 1);
        List<RollingWindowEntry> entries = members == null ? List.of() : members.stream()
                .map(this::readMember)
                .flatMap(java.util.Optional::stream)
                .toList();
        return snapshotCalculator.snapshot(entries, windowStart, currentTimestamp);
    }

    @Override
    public void add(AnomalyGroupKey key, RollingWindowEntry entry) {
        String redisKey = redisKey(key);
        try {
            RedisRollingWindowEntry member = new RedisRollingWindowEntry(UUID.randomUUID().toString(), entry);
            redisTemplate.opsForZSet().add(redisKey, objectMapper.writeValueAsString(member), entry.timestamp().toEpochMilli());
            Duration retention = properties.getRolling().getWindowSize().plus(properties.getRolling().getLateTolerance());
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, entry.timestamp().minus(retention).toEpochMilli() - 1);
            redisTemplate.expire(redisKey, retention.plus(Duration.ofMinutes(1)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize rolling window entry", exception);
        }
    }

    private java.util.Optional<RollingWindowEntry> readMember(String member) {
        try {
            RedisRollingWindowEntry wrapper = objectMapper.readValue(member, RedisRollingWindowEntry.class);
            return java.util.Optional.ofNullable(wrapper.entry());
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Skipping malformed Redis rolling window member");
            return java.util.Optional.empty();
        }
    }

    private String redisKey(AnomalyGroupKey key) {
        return KEY_PREFIX + key.serviceId() + "|" + key.endpointId() + "|" + key.flowType();
    }
}
