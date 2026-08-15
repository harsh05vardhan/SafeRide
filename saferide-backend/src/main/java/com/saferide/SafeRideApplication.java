package com.saferide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

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
 *
 * <p>{@link EnableSpringDataWebSupport} with {@code VIA_DTO} makes every
 * {@code Page<T>} a controller returns (admin list endpoints, starting
 * here with {@code GET /api/admin/drivers}) serialize as a stable
 * {@code PagedModel} instead of raw {@code PageImpl} - the latter has no
 * public constructor Jackson can use for the reverse direction and Spring
 * warns loudly about it otherwise.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class SafeRideApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeRideApplication.class, args);
    }
}
