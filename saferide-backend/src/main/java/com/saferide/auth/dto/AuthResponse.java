package com.saferide.auth.dto;

import com.saferide.user.dto.UserSummaryDTO;

/** Returned by register/login/refresh. {@code expiresIn} is seconds until the access token expires. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummaryDTO user) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs, UserSummaryDTO user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000, user);
    }
}
