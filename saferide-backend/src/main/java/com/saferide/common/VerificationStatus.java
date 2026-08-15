package com.saferide.common;

/**
 * Shared admin-review lifecycle for anything that needs one:
 * {@link com.saferide.driver.Driver}, {@link com.saferide.vehicle.Vehicle},
 * {@link com.saferide.driver.DriverDocument}. Kept as a single enum rather
 * than a copy per entity so "what does PENDING mean here" never has more
 * than one answer.
 */
public enum VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
