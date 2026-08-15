package com.saferide.ride.dto;

import com.saferide.ride.RideStatus;
import java.time.Instant;

/**
 * Broadcast to {@code /topic/rides/{id}/status} on every transition.
 * Deliberately minimal - no otp, no customer/driver contact details, none
 * of the fields {@code RideResponse} varies by caller. This channel is
 * shared by both the ride's customer and its driver, so it can only ever
 * carry what's safe for either of them to see; whichever recipient needs
 * more detail already has it via {@code GET /api/rides/{id}}, which this
 * event exists to tell them to call again right now instead of waiting for
 * the next poll.
 */
public record RideStatusEvent(Long rideId, RideStatus status, Instant updatedAt) {
}
