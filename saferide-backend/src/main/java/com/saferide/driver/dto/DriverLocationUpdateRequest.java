package com.saferide.driver.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/drivers/location}. Writes straight to MySQL (the source
 * of truth per the architecture) - Phase 11 adds Redis as the
 * high-frequency hot path for this same data, not a replacement for it.
 */
public record DriverLocationUpdateRequest(
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double latitude,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double longitude) {
}
