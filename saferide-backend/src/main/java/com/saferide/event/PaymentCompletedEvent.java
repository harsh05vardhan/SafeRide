package com.saferide.event;

import java.time.Instant;

public record PaymentCompletedEvent(
        Long rideId, Long customerUserId, Long driverUserId, double amount, String currency, Instant occurredAt)
        implements DomainEvent {
}
