package com.saferide.safety.dto;

import com.saferide.safety.SafetyAlert;
import com.saferide.safety.SafetyAlertStatus;
import com.saferide.user.dto.UserSummaryDTO;
import java.time.Instant;

public record SafetyAlertResponse(
        Long id,
        Long rideId,
        UserSummaryDTO triggeredBy,
        double latitude,
        double longitude,
        SafetyAlertStatus status,
        Instant createdAt,
        Instant resolvedAt) {

    public static SafetyAlertResponse from(SafetyAlert alert) {
        return new SafetyAlertResponse(
                alert.getId(),
                alert.getRide().getId(),
                UserSummaryDTO.from(alert.getTriggeredBy()),
                alert.getLatitude(),
                alert.getLongitude(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getResolvedAt());
    }
}
