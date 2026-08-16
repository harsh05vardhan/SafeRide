package com.saferide.event;

import java.time.Instant;

/** Published once a ride is created (still {@code REQUESTED} - see the Fare Lock note on {@code RideService}). */
public record RideRequestedEvent(Long rideId, Long customerUserId, Instant occurredAt) implements DomainEvent {
}
