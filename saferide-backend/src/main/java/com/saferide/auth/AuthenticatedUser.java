package com.saferide.auth;

import java.util.Set;

/**
 * The principal placed into Spring Security's context by
 * {@link JwtAuthenticationFilter}. Built directly from validated JWT
 * claims - not read back from the database - so every controller that
 * needs "who is calling" gets it via
 * {@code @AuthenticationPrincipal AuthenticatedUser user} at effectively
 * zero cost. {@link JwtAuthenticationFilter} does still perform one
 * lightweight database check per request (the caller's current account
 * status), but that's a gate on whether this principal gets set at all,
 * not something this record itself carries.
 */
public record AuthenticatedUser(Long userId, String email, Set<String> roles) {
}
