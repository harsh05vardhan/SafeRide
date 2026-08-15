package com.saferide.driver;

/** Toggled by the driver (PUT /api/drivers/status, Phase 3). Matching only ever considers ONLINE drivers. */
public enum DriverOnlineStatus {
    ONLINE,
    OFFLINE
}
