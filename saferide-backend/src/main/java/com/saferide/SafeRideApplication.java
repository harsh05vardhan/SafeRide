package com.saferide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * SafeRide backend entry point.
 *
 * <p>Modular monolith organized by business domain (auth, user, driver, vehicle,
 * ride, fare, matching, payment, rating, safety, notification, complaint, admin).
 * Each domain package is self-contained: controller -> service -> repository -> entity,
 * and never exposes JPA entities directly over REST (DTOs only).
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because SafeRide
 * authenticates entirely through {@code JwtAuthenticationFilter} against the
 * {@code users} table - without this exclusion, Spring Boot generates and logs
 * an unused random in-memory user/password on every startup.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SafeRideApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeRideApplication.class, args);
    }
}
