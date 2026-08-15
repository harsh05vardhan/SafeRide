package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a requested entity (user, driver, ride, ...) does not exist. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " not found: " + id);
    }
}
