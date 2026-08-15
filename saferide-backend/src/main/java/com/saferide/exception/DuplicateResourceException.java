package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown on a unique-constraint violation the caller can fix (e.g. email already registered). */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message, String code) {
        super(message, HttpStatus.CONFLICT, code);
    }
}
