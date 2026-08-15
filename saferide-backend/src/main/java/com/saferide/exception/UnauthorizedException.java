package com.saferide.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown from service-layer code for authorization checks that go beyond
 * what {@code @PreAuthorize} role checks can express - e.g. "this ride
 * does not belong to the authenticated customer". Role-only checks are
 * handled by Spring Security and surface as 403 via
 * {@link com.saferide.config.JwtAccessDeniedHandler} instead.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED");
    }
}
