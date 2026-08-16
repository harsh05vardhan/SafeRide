package com.saferide.auth;

import com.saferide.exception.TooManyAttemptsException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * One Redis string counter per identifier ({@value #KEY_PREFIX}{@code <identifier>}),
 * incremented on every failed attempt with {@code EXPIRE} set only the
 * first time (so the window is "N minutes since the first recent
 * failure", not extended by each subsequent one) - the standard
 * fixed-window counter pattern. Shared across every backend instance,
 * unlike {@link InMemoryLoginRateLimiter}.
 *
 * <p><b>Fails open.</b> If Redis is unreachable, {@link #checkAllowed}
 * logs and allows the attempt through rather than throwing - a rate
 * limiter that itself becomes a way to lock every user out of login the
 * moment its backing store hiccups would trade a minor security control
 * for a major availability one. {@link #recordFailure}/{@link #recordSuccess}
 * degrade the same way: a failure to update the counter just means this
 * one attempt isn't tracked, not a request failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.security.login-rate-limit", name = "source", havingValue = "redis", matchIfMissing = true)
public class RedisLoginRateLimiter implements LoginRateLimiter {

    private static final String KEY_PREFIX = "login:attempts:";

    private final StringRedisTemplate redisTemplate;
    private final LoginRateLimitProperties properties;

    @Override
    public void checkAllowed(String identifier) {
        try {
            String raw = redisTemplate.opsForValue().get(key(identifier));
            int attempts = raw == null ? 0 : Integer.parseInt(raw);
            if (attempts >= properties.getMaxAttempts()) {
                throw new TooManyAttemptsException("Too many failed login attempts. Please try again in a few minutes.");
            }
        } catch (TooManyAttemptsException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Login rate limit check failed open (Redis unreachable?): {}", ex.getMessage());
        }
    }

    @Override
    public void recordFailure(String identifier) {
        try {
            String redisKey = key(identifier);
            Long attempts = redisTemplate.opsForValue().increment(redisKey);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(redisKey, Duration.ofMinutes(properties.getWindowMinutes()));
            }
        } catch (Exception ex) {
            log.warn("Could not record a failed login attempt in Redis: {}", ex.getMessage());
        }
    }

    @Override
    public void recordSuccess(String identifier) {
        try {
            redisTemplate.delete(key(identifier));
        } catch (Exception ex) {
            log.warn("Could not clear the login attempt counter in Redis: {}", ex.getMessage());
        }
    }

    private String key(String identifier) {
        return KEY_PREFIX + identifier;
    }
}
