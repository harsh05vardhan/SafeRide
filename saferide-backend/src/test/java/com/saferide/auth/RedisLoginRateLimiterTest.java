package com.saferide.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferide.exception.TooManyAttemptsException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Verifies the Redis command shape (INCR + EXPIRE-only-on-first-increment, DELETE on success) and that a Redis failure fails open - see the class doc. */
@ExtendWith(MockitoExtension.class)
class RedisLoginRateLimiterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisLoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        properties.setMaxAttempts(5);
        properties.setWindowMinutes(15);
        limiter = new RedisLoginRateLimiter(redisTemplate, properties);
    }

    @Test
    void allowsWhenBelowTheConfiguredMaximum() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:attempts:a@example.com")).thenReturn("4");

        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void blocksAtTheConfiguredMaximum() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:attempts:a@example.com")).thenReturn("5");

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void recordFailureSetsExpiryOnlyOnTheFirstIncrement() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:attempts:a@example.com")).thenReturn(1L);

        limiter.recordFailure("a@example.com");

        verify(redisTemplate).expire("login:attempts:a@example.com", Duration.ofMinutes(15));
    }

    @Test
    void recordFailureDoesNotResetExpiryOnSubsequentIncrements() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:attempts:a@example.com")).thenReturn(2L);

        limiter.recordFailure("a@example.com");

        verify(redisTemplate, org.mockito.Mockito.never()).expire(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordSuccessDeletesTheCounter() {
        limiter.recordSuccess("a@example.com");

        verify(redisTemplate).delete("login:attempts:a@example.com");
    }

    @Test
    void aRedisFailureOnCheckAllowedFailsOpenRatherThanBlockingLogin() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void aRedisFailureOnRecordFailureIsSwallowed() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> limiter.recordFailure("a@example.com")).doesNotThrowAnyException();
    }
}
