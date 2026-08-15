package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a driver tries to do something (go online, later: accept a ride) that requires admin verification first. */
public class DriverNotVerifiedException extends ApiException {

    public DriverNotVerifiedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "DRIVER_NOT_VERIFIED");
    }
}
