package com.saferide.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown for a failed login (unknown identifier or wrong password). The
 * message is intentionally generic - never reveal which of the two was
 * wrong, so the endpoint can't be used to enumerate registered accounts.
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super("Invalid email/phone or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }
}
