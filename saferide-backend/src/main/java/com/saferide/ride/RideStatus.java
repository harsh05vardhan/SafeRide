package com.saferide.ride;

/** The full ride lifecycle. See {@link RideStateMachine} for which transitions between these are legal. */
public enum RideStatus {
    REQUESTED,
    SEARCHING_DRIVER,
    DRIVER_ASSIGNED,
    DRIVER_ACCEPTED,
    DRIVER_ARRIVED,
    RIDE_STARTED,
    RIDE_COMPLETED,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    RATED,
    CANCELLED,
    DRIVER_CANCELLED
}
