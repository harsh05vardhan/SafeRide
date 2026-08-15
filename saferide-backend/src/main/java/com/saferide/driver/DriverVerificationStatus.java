package com.saferide.driver;

/** Set by an admin (Phase 3 verification workflow). A driver cannot accept rides until VERIFIED. */
public enum DriverVerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
