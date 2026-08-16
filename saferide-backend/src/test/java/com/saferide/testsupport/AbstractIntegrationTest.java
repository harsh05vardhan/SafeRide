package com.saferide.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every integration test that boots a full Spring context -
 * a real MySQL and a real Redis (Phase 14), not the H2/SQL-fallback/
 * in-memory stand-ins the suite used through Phase 13. "Singleton
 * containers" pattern: one {@link MySQLContainer} and one Redis
 * {@link GenericContainer} for the entire test JVM, started once in a
 * static initializer and never explicitly stopped (Testcontainers' own
 * Ryuk reaper removes them when the JVM exits) - not one pair per test
 * class, which would make a ~20-class suite unusably slow. Every
 * subclass sharing this exact configuration also shares one cached Spring
 * context, same as before.
 *
 * <p>{@code app.matching.geo-index-source}/{@code app.security.login-rate-limit.source}
 * are switched to {@code redis} here too - the whole point of a real
 * container is exercising {@code RedisDriverGeoIndex}/{@code RedisLoginRateLimiter}
 * themselves through real HTTP requests, not their SQL/in-memory
 * fallbacks (which keep their own dedicated, fast, no-context unit tests -
 * see {@code SqlDriverGeoIndexTest}/{@code InMemoryLoginRateLimiterTest} -
 * that responsibility didn't move).
 *
 * <p>{@code KafkaEventForwarderTest} deliberately does NOT extend this -
 * it already needs its own one-off Spring context (different Kafka
 * properties, {@code @DirtiesContext}) and reasons about a Kafka broker
 * of its own ({@code @EmbeddedKafka}) independently of matching/rate
 * limiting, so it gets its own small dedicated MySQL container instead
 * of borrowing this shared one - simpler than fighting
 * {@code @DynamicPropertySource} override ordering between a shared base
 * class and one test's need for an isolated schema.
 */
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("saferide_test")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("serverTimezone", "UTC");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("app.matching.geo-index-source", () -> "redis");
        registry.add("app.security.login-rate-limit.source", () -> "redis");
    }
}
