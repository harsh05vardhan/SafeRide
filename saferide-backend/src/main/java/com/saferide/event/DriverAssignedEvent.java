package com.saferide.event;

import java.time.Instant;

/** Published the moment matching proactively assigns a driver - see {@code RideService.attemptAutoAssign}. */
public record DriverAssignedEvent(Long rideId, Long customerUserId, Long driverUserId, Instant occurredAt) implements DomainEvent {
}
