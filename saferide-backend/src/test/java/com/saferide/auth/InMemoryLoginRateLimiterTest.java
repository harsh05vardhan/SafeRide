package com.saferide.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saferide.exception.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A real implementation, not a mock of {@link RedisLoginRateLimiter} - see its class doc - so this exercises the actual enforcement logic directly. */
class InMemoryLoginRateLimiterTest {

    private InMemoryLoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        properties.setMaxAttempts(3);
        properties.setWindowMinutes(15);
        limiter = new InMemoryLoginRateLimiter(properties);
    }

    @Test
    void allowsAttemptsUpToTheLimit() {
        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
        limiter.recordFailure("a@example.com");
        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
        limiter.recordFailure("a@example.com");
        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void blocksOnceTheLimitIsReached() {
        limiter.recordFailure("a@example.com");
        limiter.recordFailure("a@example.com");
        limiter.recordFailure("a@example.com"); // 3rd failure - maxAttempts reached

        assertThatThrownBy(() -> limiter.checkAllowed("a@example.com"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void aSuccessClearsTheCounterEntirely() {
        limiter.recordFailure("a@example.com");
        limiter.recordFailure("a@example.com");
        limiter.recordFailure("a@example.com");

        limiter.recordSuccess("a@example.com");

        assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void identifiersAreTrackedIndependently() {
        limiter.recordFailure("a@example.com");
        limiter.recordFailure("a@example.com");
        limiter.recordFailure("a@example.com");

        // b@example.com has never failed - unaffected by a@example.com's counter.
        assertThatCode(() -> limiter.checkAllowed("b@example.com")).doesNotThrowAnyException();
    }
}
