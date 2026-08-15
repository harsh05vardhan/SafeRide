package com.saferide.auth.dto;

import com.saferide.user.RoleName;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Signup payload for both customers and drivers (mirrors the single
 * signup form with a role picker described in the UI spec). When
 * {@code role} is {@code DRIVER}, {@code licenseNumber} is required and a
 * minimal {@code Driver} profile (verification PENDING) is created
 * alongside the account; vehicle details are attached separately once the
 * Vehicle entity exists (Phase 3). Registering as {@code ADMIN} is
 * rejected - admin accounts are provisioned out of band, never
 * self-served.
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be a valid 10-15 digit number")
        String phone,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "Password must be at least 8 characters and include a letter and a digit")
        String password,

        @NotNull(message = "Role is required")
        RoleName role,

        @Pattern(regexp = "^[A-Za-z0-9-]{4,30}$", message = "License number looks invalid")
        String licenseNumber) {

    @AssertTrue(message = "License number is required when registering as a driver")
    public boolean isLicenseValidForRole() {
        return role != RoleName.DRIVER || (licenseNumber != null && !licenseNumber.isBlank());
    }
}
