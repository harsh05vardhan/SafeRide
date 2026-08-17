package com.saferide.ride;

/** Why a ride ended up {@code CANCELLED}/{@code DRIVER_CANCELLED} - lets the customer's UI tell "you cancelled", "your driver cancelled" and "nobody was available in time" apart instead of showing one generic message for all three. */
public enum RideCancellationReason {
    CUSTOMER,
    DRIVER,
    NO_DRIVER_FOUND
}
