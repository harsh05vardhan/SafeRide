package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an admin tries to resolve/dismiss a complaint that's already in a terminal state. */
public class InvalidComplaintStateException extends ApiException {

    public InvalidComplaintStateException(String message) {
        super(message, HttpStatus.CONFLICT, "INVALID_COMPLAINT_STATE");
    }
}
