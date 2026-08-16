package com.saferide.auth;

import com.saferide.exception.TooManyAttemptsException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A real, fully-functional fixed-window counter - not a mock of
 * {@link RedisLoginRateLimiter}, just per-instance rather than shared -
 * active whenever {@code app.security.login-rate-limit.source=in-memory}
 * (the test profile's default, since Redis isn't available there yet;
 * also a legitimate single-instance deployment choice).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.security.login-rate-limit", name = "source", havingValue = "in-memory")
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    private final LoginRateLimitProperties properties;
    private final Map<String, Attempts> attemptsByIdentifier = new ConcurrentHashMap<>();

    @Override
    public void checkAllowed(String identifier) {
        Attempts attempts = attemptsByIdentifier.get(identifier);
        if (attempts == null || isExpired(attempts)) {
            return;
        }
        if (attempts.count() >= properties.getMaxAttempts()) {
            throw new TooManyAttemptsException("Too many failed login attempts. Please try again in a few minutes.");
        }
    }

    @Override
    public void recordFailure(String identifier) {
        attemptsByIdentifier.compute(identifier, (key, existing) ->
                existing == null || isExpired(existing)
                        ? new Attempts(1, Instant.now())
                        : new Attempts(existing.count() + 1, existing.windowStart()));
    }

    @Override
    public void recordSuccess(String identifier) {
        attemptsByIdentifier.remove(identifier);
    }

    private boolean isExpired(Attempts attempts) {
        return Instant.now().isAfter(attempts.windowStart().plus(Duration.ofMinutes(properties.getWindowMinutes())));
    }

    private record Attempts(int count, Instant windowStart) {
    }
}
