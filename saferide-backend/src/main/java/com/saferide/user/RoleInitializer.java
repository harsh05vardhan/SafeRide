package com.saferide.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the three fixed {@link RoleName} rows exist. Runs in every
 * profile (dev, test, prod) - unlike {@code DevDataSeeder}'s demo users,
 * roles are structural reference data the application cannot function
 * without, not test fixtures.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInitializer {

    private final RoleRepository roleRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void seedRoles() {
        for (RoleName roleName : RoleName.values()) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                roleRepository.save(new Role(roleName));
                log.info("Seeded role: {}", roleName);
            }
        }
    }
}
