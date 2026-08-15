package com.saferide.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/customers/profile} body. Email is intentionally not
 * editable here - changing the primary identifier belongs behind its own
 * verification flow, out of scope for Phase 2.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be a valid 10-15 digit number")
        String phone) {
}
