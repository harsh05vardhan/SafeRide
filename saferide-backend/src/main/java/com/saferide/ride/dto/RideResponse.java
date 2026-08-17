package com.saferide.ride.dto;

import com.saferide.ride.RideCancellationReason;
import com.saferide.ride.RideStatus;
import com.saferide.user.dto.UserSummaryDTO;
import com.saferide.vehicle.VehicleType;
import java.time.Instant;

/**
 * {@code otp} is only ever populated when the response is being built for
 * the ride's own customer (see {@code RideService#toResponse}'s
 * {@code includeOtp} parameter) - the driver must be told the code by the
 * rider, never read it from the API themselves.
 *
 * <p>{@code searchingSince} is only meaningful while {@code status} is
 * {@code SEARCHING_DRIVER} - the frontend's countdown derives its
 * remaining time from it directly rather than trusting a locally-started
 * timer, so a page reload mid-search still shows the correct time left.
 * {@code cancellationReason} is only meaningful once {@code status} is
 * {@code CANCELLED}/{@code DRIVER_CANCELLED} - lets the UI show "no driver
 * was available" distinctly from a customer- or driver-initiated cancel.
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
        Instant cancelledAt,
        RideCancellationReason cancellationReason,
        Instant searchingSince) {
}
