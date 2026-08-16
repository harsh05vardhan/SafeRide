package com.saferide.notification;

/** One value per {@code com.saferide.event.DomainEvent} concrete type {@link NotificationEventListener} reacts to. */
public enum NotificationType {
    RIDE_REQUESTED,
    DRIVER_ASSIGNED,
    RIDE_COMPLETED,
    PAYMENT_COMPLETED,
    COMPLAINT_FILED,
    COMPLAINT_RESOLVED
}
