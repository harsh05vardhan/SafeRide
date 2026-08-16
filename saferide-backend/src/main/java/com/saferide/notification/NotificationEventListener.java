package com.saferide.notification;

import com.saferide.event.ComplaintFiledEvent;
import com.saferide.event.ComplaintResolvedEvent;
import com.saferide.event.DomainEvent;
import com.saferide.event.DriverAssignedEvent;
import com.saferide.event.PaymentCompletedEvent;
import com.saferide.event.RideCompletedEvent;
import com.saferide.event.RideRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The one place a {@code com.saferide.event.DomainEvent} becomes an
 * in-app {@link Notification}. Runs {@code AFTER_COMMIT} - same reasoning
 * as {@code RideEventPublisher}'s WebSocket broadcasts (Phase 10): a
 * notification must never describe something a rolled-back transaction
 * undid. Unlike that class, this needs no manual
 * {@code TransactionSynchronizationManager} bookkeeping -
 * {@code @TransactionalEventListener} is Spring's own built-in equivalent,
 * usable here because this is a plain event-listener method, not a
 * REST-controller-callable API.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        switch (event) {
            case RideRequestedEvent e -> notificationService.create(
                    e.customerUserId(), NotificationType.RIDE_REQUESTED,
                    "Ride requested", "We're finding you a driver nearby.", e.rideId());

            case DriverAssignedEvent e -> {
                notificationService.create(
                        e.customerUserId(), NotificationType.DRIVER_ASSIGNED,
                        "Driver assigned", "A driver has been assigned to your ride.", e.rideId());
                notificationService.create(
                        e.driverUserId(), NotificationType.DRIVER_ASSIGNED,
                        "New ride request", "You've been assigned a new ride - accept it to get started.", e.rideId());
            }

            case RideCompletedEvent e -> {
                notificationService.create(
                        e.customerUserId(), NotificationType.RIDE_COMPLETED,
                        "Ride completed", "Your trip has ended. Please complete payment.", e.rideId());
                notificationService.create(
                        e.driverUserId(), NotificationType.RIDE_COMPLETED,
                        "Trip completed", "You've completed the trip successfully.", e.rideId());
            }

            case PaymentCompletedEvent e -> {
                notificationService.create(
                        e.customerUserId(), NotificationType.PAYMENT_COMPLETED,
                        "Payment successful", "₹%.0f paid for your ride.".formatted(e.amount()), e.rideId());
                notificationService.create(
                        e.driverUserId(), NotificationType.PAYMENT_COMPLETED,
                        "Payment received", "The rider paid ₹%.0f for the trip.".formatted(e.amount()), e.rideId());
            }

            case ComplaintFiledEvent e -> notificationService.create(
                    e.filedByUserId(), NotificationType.COMPLAINT_FILED,
                    "Report submitted", "We've received your report and will review it shortly.", e.rideId());

            case ComplaintResolvedEvent e -> notificationService.create(
                    e.filedByUserId(), NotificationType.COMPLAINT_RESOLVED,
                    "Report " + e.status().toLowerCase(),
                    e.status().equals("RESOLVED")
                            ? "Your report has been reviewed and resolved."
                            : "Your report has been reviewed and closed without further action.",
                    null);

            default -> { /* not every DomainEvent needs a notification */ }
        }
    }
}
