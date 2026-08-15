package com.saferide.ride.dto;

import com.saferide.ride.RideStatus;
import com.saferide.user.dto.UserSummaryDTO;
import com.saferide.vehicle.VehicleType;
import java.time.Instant;

/**
 * {@code otp} is only ever populated when the response is being built for
 * the ride's own customer (see {@code RideService#toResponse}'s
 * {@code includeOtp} parameter) - the driver must be told the code by the
 * rider, never read it from the API themselves.
 */
public record RideResponse(
        Long id,
        UserSummaryDTO customer,
        RideDriverInfo driver,
        VehicleType vehicleType,
        String pickupAddress,
        String destinationAddress,
        Double pickupLatitude,
        Double pickupLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        Double distanceKm,
        Integer estimatedDurationMinutes,
        RideStatus status,
        String otp,
        Instant createdAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt) {
}
