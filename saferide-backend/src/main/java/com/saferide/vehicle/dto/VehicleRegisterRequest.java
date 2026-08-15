package com.saferide.vehicle.dto;

import com.saferide.vehicle.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VehicleRegisterRequest(
        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @NotBlank(message = "Vehicle model is required")
        @Pattern(regexp = "^.{2,60}$", message = "Vehicle model must be between 2 and 60 characters")
        String vehicleModel,

        @NotBlank(message = "Vehicle number is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{4,20}$", message = "Vehicle number looks invalid")
        String vehicleNumber,

        @NotBlank(message = "Vehicle color is required")
        String vehicleColor,

        @NotNull(message = "Seat capacity is required")
        @Min(value = 1, message = "Seat capacity must be at least 1")
        @Max(value = 10, message = "Seat capacity must be at most 10")
        Integer seatCapacity) {
}
