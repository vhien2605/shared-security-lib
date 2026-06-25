package vdt.mini.management_service.service.anomaly.alert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailAlertThrottleServiceTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void acquire_shouldUseIncidentKeyAndRespectExistingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("security:anomaly:alert:email:inc-1", "1", Duration.ofMinutes(5)))
                .thenReturn(true).thenReturn(false);
        EmailAlertThrottleService service = new EmailAlertThrottleService(redisTemplate);

        assertThat(service.acquire("inc-1", 5)).isTrue();
        assertThat(service.acquire("inc-1", 5)).isFalse();
    }
}
