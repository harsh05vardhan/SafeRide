package com.saferide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SafeRide backend entry point.
 *
 * <p>Modular monolith organized by business domain (auth, user, driver, vehicle,
 * ride, fare, matching, payment, rating, safety, notification, complaint, admin).
 * Each domain package is self-contained: controller -> service -> repository -> entity,
 * and never exposes JPA entities directly over REST (DTOs only).
 */
@SpringBootApplication
public class SafeRideApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeRideApplication.class, args);
    }
}
