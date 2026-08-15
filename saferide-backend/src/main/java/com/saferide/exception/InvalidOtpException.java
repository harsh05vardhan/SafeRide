package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a driver's entered OTP doesn't match the ride's - the only gate on DRIVER_ARRIVED -> RIDE_STARTED. */
public class InvalidOtpException extends ApiException {

    public InvalidOtpException() {
        super("The OTP entered does not match", HttpStatus.BAD_REQUEST, "INVALID_OTP");
    }
}
