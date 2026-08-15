package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when confirming a fare that's already locked - the exact "Fare is already locked" scenario from the spec. */
public class FareLockedException extends ApiException {

    public FareLockedException() {
        super("Fare is already locked", HttpStatus.CONFLICT, "FARE_LOCKED");
    }
}
