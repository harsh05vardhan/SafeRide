package com.saferide.ride;

import com.saferide.ride.dto.DriverLocationEvent;
import com.saferide.ride.dto.RideAssignmentEvent;
import com.saferide.ride.dto.RideStatusEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The one place every WebSocket broadcast in the app goes through - see
 * {@code WebSocketConfig} for the destination shapes.
 *
 * <p><b>Every publish happens only after the enclosing transaction
 * commits.</b> {@code RideService}'s mutating methods call these from
 * inside an {@code @Transactional} method, before it returns - if this
 * published immediately, a client could be told a ride reached e.g.
 * {@code DRIVER_ACCEPTED} moments before a concurrent {@code @Version}
 * conflict rolls that exact save back (see the Concurrency section of the
 * README). Registering an {@code afterCommit} synchronization instead
 * means a broadcast only ever describes a state that's actually durable.
 */
@Component
@RequiredArgsConstructor
public class RideEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatusChange(Ride ride) {
        Long rideId = ride.getId();
        RideStatus status = ride.getStatus();
        runAfterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/rides/" + rideId + "/status",
                new RideStatusEvent(rideId, status, Instant.now())));
    }

    /** Privately nudges the driver matching just assigned - see {@link RideAssignmentEvent}. */
    public void publishAssignment(Ride ride) {
        if (ride.getDriver() == null) {
            return;
        }
        Long rideId = ride.getId();
        RideStatus status = ride.getStatus();
        String driverUserId = String.valueOf(ride.getDriver().getUser().getId());
        runAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                driverUserId, "/queue/assignments", new RideAssignmentEvent(rideId, status)));
    }

    public void publishDriverLocation(Long rideId, Long driverId, double latitude, double longitude) {
        runAfterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/rides/" + rideId + "/location",
                new DriverLocationEvent(rideId, driverId, latitude, longitude, Instant.now())));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            // No transaction in progress (shouldn't happen from the call sites this is used from
            // today, but publishing directly is still correct - there's nothing to roll back).
            action.run();
        }
    }
}
