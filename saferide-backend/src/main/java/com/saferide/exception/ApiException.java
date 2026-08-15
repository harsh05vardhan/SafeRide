package com.saferide.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for every business exception in SafeRide. Carries the HTTP
 * status and machine-readable error code it should surface as, so
 * {@link GlobalExceptionHandler} can translate any subclass into a
 * consistent {@code ApiErrorResponse} with a single handler instead of one
 * per exception type. New domain exceptions (fare, ride state, OTP,
 * payment, ...) added in later phases just extend this.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
