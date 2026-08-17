package com.saferide.ride;

import java.util.EnumSet;
import java.util.Set;

/**
 * The two terminal-outcome status sets - shared between
 * {@code AdminAnalyticsService}'s platform-wide totals and
 * {@code AdminDriverService}'s per-driver completed/cancelled ride counts,
 * so "what counts as completed" and "what counts as cancelled" can never
 * drift between the two screens the way {@link ActiveRideStatuses} already
 * protects "what counts as busy".
 */
public final class TerminalRideStatuses {

    /** A ride that reached RIDE_COMPLETED and is anywhere in the payment/rating tail after that. */
    public static final Set<RideStatus> COMPLETED =
            EnumSet.of(RideStatus.RIDE_COMPLETED, RideStatus.PAYMENT_PENDING, RideStatus.PAYMENT_COMPLETED, RideStatus.RATED);

    public static final Set<RideStatus> CANCELLED = EnumSet.of(RideStatus.CANCELLED, RideStatus.DRIVER_CANCELLED);

    private TerminalRideStatuses() {
    }
}
