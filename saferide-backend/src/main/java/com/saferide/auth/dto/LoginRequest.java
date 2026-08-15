package com.saferide.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code identifier} accepts either an email or a phone number - the signup/login UI supports both. */
public record LoginRequest(
        @NotBlank(message = "Email or phone is required") String identifier,
        @NotBlank(message = "Password is required") String password) {
}
