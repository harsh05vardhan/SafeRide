package com.saferide;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferide.common.PingController;
import com.saferide.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies the full application context wires up (JPA,
 * datasource, MVC, OpenAPI, CORS config) against a real MySQL + Redis
 * (Phase 14, via Testcontainers - see AbstractIntegrationTest). Deeper
 * domain tests arrive alongside their features starting Phase 2.
 */
@SpringBootTest
class SafeRideApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private PingController pingController;

    @Test
    void contextLoads() {
        assertThat(pingController).isNotNull();
    }

    @Test
    void pingReturnsUpStatus() {
        var response = pingController.ping();
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("status", "UP");
    }
}
