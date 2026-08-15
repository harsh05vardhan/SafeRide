package com.saferide.config;

import com.saferide.driver.Driver;
import com.saferide.driver.DriverRepository;
import com.saferide.driver.DriverService;
import com.saferide.driver.DriverVerificationStatus;
import com.saferide.user.RoleName;
import com.saferide.user.User;
import com.saferide.user.UserRepository;
import com.saferide.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEVELOPMENT-ONLY fixture accounts, active only under the {@code dev}
 * profile - never runs in {@code test} or {@code prod}. Credentials below
 * are intentionally public knowledge; do not reuse them anywhere real.
 *
 * <pre>
 *   customer@example.com / Password123!  (CUSTOMER)
 *   driver@example.com   / Password123!  (DRIVER, pre-verified for convenience)
 *   admin@example.com    / Password123!  (ADMIN)
 * </pre>
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder {

    private static final String DEV_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final UserService userService;
    private final DriverService driverService;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2) // after RoleInitializer (@Order(1)) so the roles this depends on already exist
    @Transactional
    public void seedDevUsers() {
        seedCustomer();
        seedDriver();
        seedAdmin();
    }

    private void seedCustomer() {
        if (userRepository.existsByEmail("customer@example.com")) {
            return;
        }
        userService.createUser("Demo Customer", "customer@example.com", "9000000001",
                passwordEncoder.encode(DEV_PASSWORD), RoleName.CUSTOMER);
        log.info("[dev-seed] customer@example.com created");
    }

    private void seedDriver() {
        if (userRepository.existsByEmail("driver@example.com")) {
            return;
        }
        User user = userService.createUser("Demo Driver", "driver@example.com", "9000000002",
                passwordEncoder.encode(DEV_PASSWORD), RoleName.DRIVER);
        Driver driver = driverService.createDriverProfile(user, "DL-DEV-0001");
        // Skip the real admin-verification workflow (Phase 3) purely so the demo
        // account can be used end-to-end (matching, rides, ...) without extra setup.
        driver.setVerificationStatus(DriverVerificationStatus.VERIFIED);
        driverRepository.save(driver);
        log.info("[dev-seed] driver@example.com created (pre-verified)");
    }

    private void seedAdmin() {
        if (userRepository.existsByEmail("admin@example.com")) {
            return;
        }
        userService.createUser("Demo Admin", "admin@example.com", "9000000003",
                passwordEncoder.encode(DEV_PASSWORD), RoleName.ADMIN);
        log.info("[dev-seed] admin@example.com created");
    }
}
