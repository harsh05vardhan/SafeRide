package com.saferide.ride.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/rides/{id}/destination} - only allowed before a driver has been assigned; see RideService.changeDestination. */
public record RideDestinationChangeRequest(
        @NotBlank @Size(max = 300) String destinationAddress,
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double destinationLatitude,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double destinationLongitude) {
}
