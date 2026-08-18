package com.saferide.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What happened and what was done - required to resolve an alert, not optional, so a resolved alert always has a real record of the outcome. */
public record ResolveSafetyAlertRequest(
        @NotBlank(message = "Describe what happened and how it was resolved")
        @Size(max = 1000, message = "Keep it under 1000 characters")
        String notes) {
}
