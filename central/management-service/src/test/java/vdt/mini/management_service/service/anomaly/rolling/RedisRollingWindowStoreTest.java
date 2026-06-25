package vdt.mini.management_service.service.anomaly.rolling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import vdt.mini.management_service.dto.event.RollingWindowEntry;
import vdt.mini.management_service.service.anomaly.runtime.AnomalyTestFixtures;
import vdt.mini.management_service.service.anomaly.stat.PercentileCalculator;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRollingWindowStoreTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;

    @Test
    void snapshotBefore_shouldReadBeforeCurrentAndKeepDuplicateIdenticalEntries() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        RollingWindowEntry entry = entry(Instant.parse("2026-06-23T00:04:00Z"));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of(
                mapper.writeValueAsString(new RedisRollingWindowEntry("one", entry)),
                mapper.writeValueAsString(new RedisRollingWindowEntry("two", entry))));

        var snapshot = store(mapper).snapshotBefore(AnomalyTestFixtures.key(), Instant.parse("2026-06-23T00:05:00Z"));

        assertThat(snapshot.windowSampleCount()).isEqualTo(2);
        verify(zSetOperations).rangeByScore("security:anomaly:rolling:svc-1|ep-1|INBOUND_HTTP",
                Instant.parse("2026-06-23T00:00:00Z").toEpochMilli(), Instant.parse("2026-06-23T00:05:00Z").toEpochMilli() - 1);
    }

    @Test
    void add_shouldWriteUuidMemberEvictOldScoresAndSetTtl() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        Instant now = Instant.parse("2026-06-23T00:10:00Z");

        store(mapper).add(AnomalyTestFixtures.key(), entry(now));

        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        verify(zSetOperations).add(eq("security:anomaly:rolling:svc-1|ep-1|INBOUND_HTTP"), memberCaptor.capture(), eq((double) now.toEpochMilli()));
        assertThat(memberCaptor.getValue()).contains("\"id\"");
        verify(zSetOperations).removeRangeByScore(anyString(), eq(0.0), eq((double) now.minus(Duration.ofMinutes(6)).toEpochMilli() - 1));
        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(7)));
    }

    private RedisRollingWindowStore store(ObjectMapper mapper) {
        return new RedisRollingWindowStore(redisTemplate, mapper, AnomalyTestFixtures.properties(), new RollingWindowSnapshotCalculator(new PercentileCalculator()));
    }

    private RollingWindowEntry entry(Instant timestamp) {
        return new RollingWindowEntry(timestamp, "SUCCESS", 10L, null, null, null, null, "client", "10.0.0.1", null, null);
    }
}
