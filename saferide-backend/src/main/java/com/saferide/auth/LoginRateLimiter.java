package com.saferide.auth;

/**
 * Slows down credential-guessing against one account - deliberately keyed
 * by login identifier (email/phone), not caller IP: the threat this
 * defends against is repeated password guesses aimed at a single known
 * account, which an IP-based limiter alone wouldn't catch from a
 * distributed attacker, and an identifier-based one does regardless of
 * where the requests come from. (IP-based limiting for the different
 * threat of one attacker spraying many accounts is a deliberate
 * non-goal here, not an oversight.)
 *
 * <p>Two implementations, chosen by {@code app.security.login-rate-limit.source}:
 * {@link RedisLoginRateLimiter} (default - shared across every backend
 * instance) and {@link InMemoryLoginRateLimiter} (a real, fully-functional
 * per-instance counter, not a mock - what the test profile uses, since
 * Redis isn't available there yet).
 */
public interface LoginRateLimiter {

    /** @throws com.saferide.exception.TooManyAttemptsException if this identifier has already failed too many times within the window */
    void checkAllowed(String identifier);

    void recordFailure(String identifier);

    /** Clears the identifier's failure count - a successful login means whatever came before no longer matters. */
    void recordSuccess(String identifier);
}
