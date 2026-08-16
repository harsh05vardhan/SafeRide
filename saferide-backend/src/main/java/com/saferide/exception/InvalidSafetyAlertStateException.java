package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an admin tries to resolve a safety alert that's already resolved. */
public class InvalidSafetyAlertStateException extends ApiException {

    public InvalidSafetyAlertStateException(String message) {
        super(message, HttpStatus.CONFLICT, "INVALID_SAFETY_ALERT_STATE");
    }
}
