package com.saferide.vehicle.dto;

import com.saferide.common.VerificationStatus;
import com.saferide.vehicle.VehicleType;

public record VehicleResponse(
        Long id,
        Long driverId,
        VehicleType vehicleType,
        String vehicleModel,
        String vehicleNumber,
        String vehicleColor,
        Integer seatCapacity,
        VerificationStatus verificationStatus,
        boolean active) {
}
