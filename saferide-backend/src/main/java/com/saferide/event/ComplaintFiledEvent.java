package com.saferide.event;

import java.time.Instant;

public record ComplaintFiledEvent(Long complaintId, Long rideId, Long filedByUserId, String type, Instant occurredAt)
        implements DomainEvent {
}
