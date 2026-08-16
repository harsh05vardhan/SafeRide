package com.saferide.event;

import java.time.Instant;

/**
 * Marker every domain event implements - what lets both
 * {@code com.saferide.notification.NotificationEventListener} and
 * {@code com.saferide.event.KafkaEventForwarder} listen for "any of these"
 * with one {@code @TransactionalEventListener(DomainEvent.class)} method
 * instead of one per concrete event type. Published via Spring's own
 * {@code ApplicationEventPublisher} - the standard, always-available,
 * in-process mechanism this single modular monolith already needs no
 * external system for; Kafka (see {@link KafkaEventForwarder}) is an
 * optional, additive mirror of the same events for consumers outside this
 * process, not the thing notifications themselves depend on.
 */
public interface DomainEvent {

    Instant occurredAt();
}
