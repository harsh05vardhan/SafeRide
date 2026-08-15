package com.saferide.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Standard error envelope for every SafeRide REST endpoint.
 *
 * <pre>
 * {
 *   "success": false,
 *   "message": "Fare is already locked",
 *   "code": "FARE_LOCKED",
 *   "timestamp": "2026-08-15T10:15:30Z"
 * }
 * </pre>
 *
 * {@code fieldErrors} is populated only for bean-validation failures (400s) and
 * omitted (via {@link JsonInclude}) for every other error type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        boolean success,
        String message,
        String code,
        Instant timestamp,
        Map<String, String> fieldErrors) {

    public static ApiErrorResponse of(String message, String code) {
        return new ApiErrorResponse(false, message, code, Instant.now(), null);
    }

    public static ApiErrorResponse of(String message, String code, Map<String, String> fieldErrors) {
        return new ApiErrorResponse(false, message, code, Instant.now(), fieldErrors);
    }
}
