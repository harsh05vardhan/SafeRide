package com.saferide.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Same shape/validation as RegisterRequest, minus the role field (always ADMIN here) - the one legitimate way to create an admin account, since AuthService#register refuses ADMIN self-signup outright. */
public record CreateAdminRequest(
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
        String password) {
}
