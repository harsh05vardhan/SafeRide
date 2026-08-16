package com.saferide.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import com.saferide.user.UserService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one test in the suite that needs a real Kafka broker - a genuine
 * in-JVM one via {@code @EmbeddedKafka}, same reasoning H2 replaces MySQL
 * elsewhere: no Docker required. Everything else in Phase 13
 * ({@code NotificationIntegrationTest}) deliberately runs with
 * {@code app.events.kafka-forwarding-enabled=false} (the test profile's
 * default) to prove in-app notifications need no Kafka at all; this class
 * re-enables forwarding for itself and checks the one thing that does:
 * a committed domain event actually lands on the topic.
 *
 * <p>Events here are published for a real, previously persisted user
 * rather than a made-up id - {@link com.saferide.notification.NotificationEventListener}
 * reacts to the exact same event and would otherwise fail its own
 * (real) foreign-key constraint, and per Spring's own
 * {@code AbstractPlatformTransactionManager.triggerAfterCommit}, an
 * exception thrown by one {@code AFTER_COMMIT} synchronization aborts the
 * rest of the chain - which would falsely sink this Kafka assertion too.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "saferide.domain-events", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "app.events.kafka-forwarding-enabled=true",
        // A DIFFERENT named H2 database than the one every other test class shares
        // (jdbc:h2:mem:saferide_test). This class needs its own Spring context anyway
        // (different Kafka properties), and @DirtiesContext below closes that context at
        // the end - with ddl-auto: create-drop, closing a context DROPS its schema. Left
        // pointed at the shared database name, that drop would wipe out the tables every
        // other test class's cached context still depends on, since DB_CLOSE_DELAY=-1
        // keeps one named in-memory instance alive and shared across all of them.
        "spring.datasource.url=jdbc:h2:mem:saferide_test_kafka;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext
class KafkaEventForwarderTest {

    @Autowired private ApplicationEventPublisher domainEvents;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;

    private KafkaMessageListenerContainer<String, String> container;
    private BlockingQueue<ConsumerRecord<String, String>> records;

    private void startConsumer() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("kafka-forwarder-test", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer());
        ContainerProperties containerProperties = new ContainerProperties("saferide.domain-events");
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, String>) records::add);
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void stopConsumer() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void committedDomainEventLandsOnTheTopic() throws Exception {
        startConsumer();

        User customer = userService.createUser("Kafka Test Customer", "kafka-forwarder-test@example.com",
                "9700000001", passwordEncoder.encode("Password123!"), RoleName.CUSTOMER);
        RideRequestedEvent event = new RideRequestedEvent(999L, customer.getId(), Instant.now());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> domainEvents.publishEvent(event));

        ConsumerRecord<String, String> record = records.poll(15, TimeUnit.SECONDS);
        assertThat(record).as("KafkaEventForwarder should have forwarded the event after commit").isNotNull();
        assertThat(record.key()).isEqualTo("RideRequestedEvent");

        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("rideId").asLong()).isEqualTo(999L);
        assertThat(payload.get("customerUserId").asLong()).isEqualTo(customer.getId());
    }

    @Test
    void eventPublishedWithoutAnyTransactionIsNeverForwarded() throws Exception {
        startConsumer();

        // No transaction at all here - AFTER_COMMIT listeners never fire for a non-transactional publish,
        // by Spring's own documented contract for @TransactionalEventListener.
        domainEvents.publishEvent(new RideRequestedEvent(998L, 122L, Instant.now()));

        ConsumerRecord<String, String> record = records.poll(3, TimeUnit.SECONDS);
        assertThat(record).as("no transaction committed, so nothing should have been forwarded").isNull();
    }
}
