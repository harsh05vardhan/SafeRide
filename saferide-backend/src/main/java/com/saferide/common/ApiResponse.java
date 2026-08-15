package com.saferide.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard success envelope for every SafeRide REST endpoint.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Ride created successfully",
 *   "data": { ... }
 * }
 * </pre>
 *
 * @param <T> type of the payload returned in {@code data}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null);
    }
}
