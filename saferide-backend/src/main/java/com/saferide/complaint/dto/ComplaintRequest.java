package com.saferide.complaint.dto;

import com.saferide.complaint.ComplaintType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintRequest(
        @NotNull(message = "Complaint type is required")
        ComplaintType type,

        @NotBlank(message = "Please describe what happened")
        @Size(max = 1000, message = "Description is too long")
        String description) {
}
