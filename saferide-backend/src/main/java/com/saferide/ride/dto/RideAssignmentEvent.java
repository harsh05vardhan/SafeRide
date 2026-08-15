package com.saferide.ride.dto;

import com.saferide.ride.RideStatus;

/**
 * Sent privately to a single driver (Spring user-destination
 * {@code /user/queue/assignments}, resolved via
 * {@code StompPrincipal.getName()}) the moment matching proactively
 * assigns them a ride - the driver's dashboard has no ride id to subscribe
 * to in advance, unlike a customer tracking their own already-created
 * ride, so this can't reuse the {@code /topic/rides/{id}/**} channel.
 * Carries only the id - the driver's client refetches
 * {@code GET /api/drivers/rides} for the full picture, same as the ride
 * status channel.
 */
public record RideAssignmentEvent(Long rideId, RideStatus status) {
}
