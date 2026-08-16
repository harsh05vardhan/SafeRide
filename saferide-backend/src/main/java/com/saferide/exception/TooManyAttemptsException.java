package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when {@code LoginRateLimiter} has seen too many recent failed login attempts for one identifier. */
public class TooManyAttemptsException extends ApiException {

    public TooManyAttemptsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
    }
}
