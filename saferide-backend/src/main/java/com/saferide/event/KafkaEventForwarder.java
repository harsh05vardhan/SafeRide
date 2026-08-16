package com.saferide.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mirrors every {@link DomainEvent} onto a Kafka topic - "optional/additive"
 * exactly as the architecture doc describes it: nothing in this app
 * actually depends on this class running. {@code NotificationEventListener}
 * reacts to the same events entirely in-process via Spring's own
 * {@code ApplicationEventPublisher}; this exists purely for consumers
 * outside this JVM (a future analytics pipeline, another service) that
 * don't exist yet.
 *
 * <p>Active only when {@code app.events.kafka-forwarding-enabled=true}
 * (the default for dev/prod; {@code false} under the test profile, since
 * the fast H2-backed suite has no broker to forward to - see
 * {@code KafkaEventForwarderTest} for the one test that turns this back on
 * against a real in-JVM broker).
 *
 * <p>{@code @Async} + a broad catch: a send happens on a background thread
 * and any failure (including "no broker reachable") is logged and
 * swallowed, never propagated - the same fail-open shape
 * {@code RedisDriverGeoIndex}/{@code RedisLoginRateLimiter} already use for
 * Redis, applied here to a second optional external system.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.events", name = "kafka-forwarding-enabled", havingValue = "true")
public class KafkaEventForwarder {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.events.topic:saferide.domain-events}")
    private String topic;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void forward(DomainEvent event) {
        String eventType = event.getClass().getSimpleName();
        try {
            // Keyed by event type so a downstream consumer can partition/filter without
            // deserializing the payload first.
            kafkaTemplate.send(topic, eventType, event);
        } catch (Exception ex) {
            log.warn("Could not forward {} to Kafka: {}", eventType, ex.getMessage());
        }
    }
}
