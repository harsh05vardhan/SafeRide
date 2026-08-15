package com.saferide.ride.dto;

import com.saferide.vehicle.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Distance/duration are deliberately not accepted here - they're always recomputed server-side from the coordinates. */
public record RideCreateRequest(
        @NotBlank @Size(max = 300) String pickupAddress,
        @NotBlank @Size(max = 300) String destinationAddress,
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double pickupLatitude,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double pickupLongitude,
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double destinationLatitude,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double destinationLongitude,
        @NotNull VehicleType vehicleType) {
}
