package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a driver tries to accept a ride they're not eligible for (unverified, offline, wrong vehicle type, already on another ride, ...). */
public class DriverUnavailableException extends ApiException {

    public DriverUnavailableException(String message) {
        super(message, HttpStatus.CONFLICT, "DRIVER_UNAVAILABLE");
    }
}
