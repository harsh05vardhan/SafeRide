package com.saferide.driver.dto;

import com.saferide.driver.DriverOnlineStatus;
import jakarta.validation.constraints.NotNull;

public record DriverStatusUpdateRequest(
        @NotNull(message = "Online status is required") DriverOnlineStatus onlineStatus) {
}
