package com.saferide.user;

/** Account status. BLOCKED users can never authenticate, even with correct credentials. */
public enum UserStatus {
    ACTIVE,
    BLOCKED
}
