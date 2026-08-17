package com.saferide.exception;

import org.springframework.http.HttpStatus;

/**
 * A dedicated code (rather than the generic {@link UnauthorizedException}
 * a blocked-account login used to throw) so the frontend can reliably tell
 * "you're blocked" apart from every other 401/403 and show a richer
 * response - specifically a way to actually reach support - instead of a
 * plain toast.
 */
public class AccountBlockedException extends ApiException {

    public AccountBlockedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "ACCOUNT_BLOCKED");
    }
}
