package com.saferide.event;

import java.time.Instant;

/** Published on both {@code resolve} and {@code dismiss} - {@code status} distinguishes them for whoever's listening. */
public record ComplaintResolvedEvent(Long complaintId, Long filedByUserId, String status, Instant occurredAt)
        implements DomainEvent {
}
