package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown by {@code RideStateMachine} when a requested status transition isn't legal from the ride's current state. */
public class InvalidRideStateException extends ApiException {

    public InvalidRideStateException(String message) {
        super(message, HttpStatus.CONFLICT, "INVALID_RIDE_STATE");
    }
}
