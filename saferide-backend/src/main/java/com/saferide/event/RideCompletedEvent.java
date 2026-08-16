package com.saferide.event;

import java.time.Instant;

public record RideCompletedEvent(Long rideId, Long customerUserId, Long driverUserId, Instant occurredAt) implements DomainEvent {
}
