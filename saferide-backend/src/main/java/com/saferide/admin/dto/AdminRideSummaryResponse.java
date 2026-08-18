package com.saferide.admin.dto;

import com.saferide.ride.RideStatus;
import com.saferide.ride.dto.RideDriverInfo;
import com.saferide.user.dto.UserSummaryDTO;
import java.time.Instant;

/** Slim ride summary for the admin dashboard's "recent rides" list - just enough to identify and link to a ride, not the full RideResponse (no OTP, no coordinates). */
public record AdminRideSummaryResponse(
        Long id,
        UserSummaryDTO customer,
        RideDriverInfo driver,
        String pickupAddress,
        String destinationAddress,
        RideStatus status,
        Instant createdAt) {
}
