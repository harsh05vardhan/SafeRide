package com.saferide.safety.dto;

import com.saferide.ride.RideStatus;

/**
 * What an unauthenticated holder of a share link sees - deliberately
 * stripped down compared to {@code RideResponse}: no OTP, no phone
 * numbers, no account ids. Enough for a friend/family member to follow a
 * trip's progress, nothing that identifies or reaches either party
 * directly.
 */
public record PublicTripResponse(
        RideStatus status,
        String pickupAddress,
        String destinationAddress,
        double pickupLatitude,
        double pickupLongitude,
        double destinationLatitude,
        double destinationLongitude,
        String driverName,
        String vehicleModel,
        String vehicleNumber) {
}
