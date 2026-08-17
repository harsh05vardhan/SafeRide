package com.saferide.settings.dto;

import jakarta.validation.constraints.Pattern;

/** {@code supportPhoneNumber} may be blank/null to intentionally clear a previously-set number - only validated when non-blank (same 10-15 digit shape RegisterRequest already uses for every other phone field in the app). */
public record UpdateSupportContactRequest(
        @Pattern(regexp = "^$|^\\+?[0-9]{10,15}$", message = "Phone must be a valid 10-15 digit number") String supportPhoneNumber) {
}
