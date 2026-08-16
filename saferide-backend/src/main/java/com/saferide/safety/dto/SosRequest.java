package com.saferide.safety.dto;

import jakarta.validation.constraints.NotNull;

public record SosRequest(
        @NotNull(message = "Latitude is required") Double latitude,
        @NotNull(message = "Longitude is required") Double longitude) {
}
