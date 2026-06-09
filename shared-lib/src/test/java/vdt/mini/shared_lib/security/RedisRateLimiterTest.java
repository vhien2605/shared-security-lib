package vdt.mini.shared_lib.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void check_shouldAllowAndSetTtl_whenFirstRequestInWindow() {
        when(valueOperations.increment(startsWith("security:runtime:ratelimit:http:in:svc:endpoint-1:client-1:"))).thenReturn(1L);

        RedisRateLimiter.RateLimitResult result = rateLimiter.check("svc", "endpoint-1", "client-1", 2, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingQuota()).isEqualTo(1L);
        verify(redisTemplate).expire(startsWith("security:runtime:ratelimit:http:in:svc:endpoint-1:client-1:"), any(Duration.class));
    }

    @Test
    void check_shouldDeny_whenCounterExceedsLimit() {
        when(valueOperations.increment(startsWith("security:runtime:ratelimit:http:in:svc:endpoint-1:client-1:"))).thenReturn(3L);

        RedisRateLimiter.RateLimitResult result = rateLimiter.check("svc", "endpoint-1", "client-1", 2, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remainingQuota()).isZero();
    }
}
