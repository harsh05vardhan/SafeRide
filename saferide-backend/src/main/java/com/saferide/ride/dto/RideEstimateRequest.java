package com.saferide.ride.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RideEstimateRequest(
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double pickupLatitude,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double pickupLongitude,
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double destinationLatitude,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double destinationLongitude) {
}
