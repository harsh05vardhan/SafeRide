package com.saferide.safety.dto;

import com.saferide.ride.RideStatus;
import com.saferide.ride.dto.RideDriverInfo;
import com.saferide.safety.SafetyAlert;
import com.saferide.safety.SafetyAlertStatus;
import com.saferide.user.dto.UserSummaryDTO;
import java.time.Instant;

/**
 * Everything an admin needs to actually resolve an SOS without leaving
 * this one screen: who triggered it and from where, plus the full ride
 * it happened on - both the customer and driver (whichever one didn't
 * trigger it), pickup/destination, and the ride's own current status -
 * not just a bare ride id the admin would have to go look up separately.
 * {@code resolvedBy}/{@code resolutionNotes} are null until resolved.
 */
public record SafetyAlertResponse(
        Long id,
        Long rideId,
        RideStatus rideStatus,
        String pickupAddress,
        String destinationAddress,
        UserSummaryDTO customer,
        RideDriverInfo driver,
        UserSummaryDTO triggeredBy,
        double latitude,
        double longitude,
        SafetyAlertStatus status,
        Instant createdAt,
        UserSummaryDTO resolvedBy,
        Instant resolvedAt,
        String resolutionNotes) {

    public static SafetyAlertResponse from(SafetyAlert alert, RideDriverInfo driverInfo) {
        var ride = alert.getRide();
        return new SafetyAlertResponse(
                alert.getId(),
                ride.getId(),
                ride.getStatus(),
                ride.getPickupAddress(),
                ride.getDestinationAddress(),
                UserSummaryDTO.from(ride.getCustomer()),
                driverInfo,
                UserSummaryDTO.from(alert.getTriggeredBy()),
                alert.getLatitude(),
                alert.getLongitude(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getResolvedBy() == null ? null : UserSummaryDTO.from(alert.getResolvedBy()),
                alert.getResolvedAt(),
                alert.getResolutionNotes());
    }
}
