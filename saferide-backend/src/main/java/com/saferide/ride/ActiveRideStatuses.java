package com.saferide.ride;

import java.util.EnumSet;
import java.util.Set;

/**
 * The two "is this customer/driver currently tied up with a ride"
 * status sets, shared between {@code RideService} and
 * {@code DriverMatchingService} so the definition of "busy" can never
 * drift between the two - a driver excluded from matching because
 * they're "busy" must be exactly the same set of statuses that blocks
 * them from accepting a second ride directly.
 */
public final class ActiveRideStatuses {

    /** A customer with a ride in any of these statuses can't book another one until it resolves. */
    public static final Set<RideStatus> CUSTOMER = EnumSet.of(
            RideStatus.REQUESTED, RideStatus.SEARCHING_DRIVER, RideStatus.DRIVER_ASSIGNED,
            RideStatus.DRIVER_ACCEPTED, RideStatus.DRIVER_ARRIVED, RideStatus.RIDE_STARTED,
            RideStatus.DRIVER_CANCELLED);

    /** A driver already committed to one of these can't be matched to, or accept, a second ride. */
    public static final Set<RideStatus> DRIVER = EnumSet.of(
            RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ACCEPTED,
            RideStatus.DRIVER_ARRIVED, RideStatus.RIDE_STARTED);

    private ActiveRideStatuses() {
    }
}
