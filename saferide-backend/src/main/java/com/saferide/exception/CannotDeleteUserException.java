package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an admin tries to delete their own account, or another admin's. */
public class CannotDeleteUserException extends ApiException {

    public CannotDeleteUserException(String message) {
        super(message, HttpStatus.CONFLICT, "CANNOT_DELETE_USER");
    }
}
