package com.saferide.auth;

import java.util.Set;

/**
 * The principal placed into Spring Security's context by
 * {@link JwtAuthenticationFilter}. Built directly from validated JWT
 * claims - no database lookup per request - so every controller that
 * needs "who is calling" gets it via
 * {@code @AuthenticationPrincipal AuthenticatedUser user} at effectively
 * zero cost.
 */
public record AuthenticatedUser(Long userId, String email, Set<String> roles) {
}
