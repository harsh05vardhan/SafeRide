package com.saferide.user;

/**
 * The three SafeRide roles. A {@link User} can hold more than one (e.g. a
 * customer who later becomes a driver keeps both), but CUSTOMER cannot
 * reach DRIVER/ADMIN endpoints and DRIVER cannot reach ADMIN endpoints -
 * enforced server-side via {@code @PreAuthorize}, never trusted from the
 * client.
 */
public enum RoleName {
    CUSTOMER,
    DRIVER,
    ADMIN
}
