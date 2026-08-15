package com.saferide.fare.dto;

import com.saferide.vehicle.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** {@code POST /api/fare/calculate} - a standalone preview, e.g. one call per vehicle card on the ride-options page. */
public record FareCalculateRequest(
        @NotNull @DecimalMin(value = "0.0", message = "Distance cannot be negative") Double distanceKm,
        @NotNull @Min(value = 0, message = "Duration cannot be negative") Integer estimatedDurationMinutes,
        @NotNull VehicleType vehicleType,
        @PositiveOrZero(message = "Discount cannot be negative") Double discount) {

    public FareCalculateRequest {
        if (discount == null) {
            discount = 0.0;
        }
    }
}
