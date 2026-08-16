package com.saferide.notification.dto;

import com.saferide.notification.Notification;
import com.saferide.notification.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id, NotificationType type, String title, String message, Long rideId, boolean read, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRideId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
