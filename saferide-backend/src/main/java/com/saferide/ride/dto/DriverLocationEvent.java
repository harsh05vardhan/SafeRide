package com.saferide.ride.dto;

import java.time.Instant;

/**
 * Broadcast to {@code /topic/rides/{id}/location} every time the assigned
 * driver reports a new position while the ride is active. Unlike
 * {@link RideStatusEvent}, this carries the real payload directly (no
 * customer/driver asymmetry to worry about - a location isn't secret from
 * either party) so the frontend can move a marker on every message with no
 * REST round trip at all.
 */
public record DriverLocationEvent(Long rideId, Long driverId, double latitude, double longitude, Instant recordedAt) {
}
