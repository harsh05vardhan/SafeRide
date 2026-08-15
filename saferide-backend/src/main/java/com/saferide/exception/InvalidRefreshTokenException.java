package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a refresh token is missing, expired, revoked, or unknown to the server. */
public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
    }
}
