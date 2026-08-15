package com.saferide.fare;

/** LOCKED means the driver has no code path to change this amount. UNLOCKED happens only when a destination change forces a recalculation. */
public enum FareLockStatus {
    LOCKED,
    UNLOCKED
}
