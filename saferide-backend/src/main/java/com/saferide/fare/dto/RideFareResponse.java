package com.saferide.fare.dto;

import com.saferide.fare.FareLockStatus;
import java.time.Instant;

/**
 * {@code GET /api/rides/{id}/fare} and the response of confirming it.
 * {@code lockStatus} is {@code null} until the customer confirms for the
 * first time - the "🔒 Fare Locked" indicator on the fare-breakdown page
 * only lights up once this is {@code LOCKED}.
 */
public record RideFareResponse(
        Long rideId,
        double baseFare,
        double distanceFare,
        double timeFare,
        double platformFee,
        double discount,
        double totalFare,
        String currency,
        FareLockStatus lockStatus,
        Double lockedAmount,
        Instant lockedAt,
        Instant unlockedAt) {
}
