package com.saferide.ride;

import static com.saferide.ride.RideStatus.CANCELLED;
import static com.saferide.ride.RideStatus.DRIVER_ACCEPTED;
import static com.saferide.ride.RideStatus.DRIVER_ARRIVED;
import static com.saferide.ride.RideStatus.DRIVER_ASSIGNED;
import static com.saferide.ride.RideStatus.DRIVER_CANCELLED;
import static com.saferide.ride.RideStatus.PAYMENT_COMPLETED;
import static com.saferide.ride.RideStatus.PAYMENT_PENDING;
import static com.saferide.ride.RideStatus.RATED;
import static com.saferide.ride.RideStatus.REQUESTED;
import static com.saferide.ride.RideStatus.RIDE_COMPLETED;
import static com.saferide.ride.RideStatus.RIDE_STARTED;
import static com.saferide.ride.RideStatus.SEARCHING_DRIVER;

import com.saferide.exception.InvalidRideStateException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for which {@link RideStatus} transitions are
 * legal. Every status change in the ride domain - whether triggered by a
 * customer, a driver, or (from Phase 6 on) the matching engine - goes
 * through {@link #validateTransition}, so "can a ride actually move from X
 * to Y" never has more than one answer and is never re-implemented ad hoc
 * per endpoint.
 *
 * <p>The graph below is the complete one from the spec, including edges no
 * Phase 4 code exercises yet - e.g. {@code DRIVER_CANCELLED -> SEARCHING_DRIVER}
 * is here for Phase 6's automatic reassignment to use as-is, and
 * {@code PAYMENT_COMPLETED -> RATED} for Phase 12's rating flow - so later
 * phases never need to touch this file to add a new caller.
 *
 * <p>{@code SEARCHING_DRIVER -> REQUESTED} was added in Phase 5: a ride
 * only ever enters {@code SEARCHING_DRIVER} with a currently-locked fare
 * (confirming the fare is what makes that transition happen - see
 * {@code RideService.confirmFare}), so if a destination change unlocks the
 * fare while a ride is already searching, the ride is pulled back to
 * {@code REQUESTED} - invisible to drivers - until the customer confirms
 * the new amount. This keeps "a driver can only ever see a ride with a
 * locked fare" true by construction, not by a separate runtime check.
 */
@Component
public class RideStateMachine {

    private static final Map<RideStatus, Set<RideStatus>> TRANSITIONS = buildTransitionGraph();

    private static Map<RideStatus, Set<RideStatus>> buildTransitionGraph() {
        Map<RideStatus, Set<RideStatus>> graph = new EnumMap<>(RideStatus.class);
        graph.put(REQUESTED, EnumSet.of(SEARCHING_DRIVER, CANCELLED));
        graph.put(SEARCHING_DRIVER, EnumSet.of(DRIVER_ASSIGNED, REQUESTED, CANCELLED));
        graph.put(DRIVER_ASSIGNED, EnumSet.of(DRIVER_ACCEPTED, SEARCHING_DRIVER, CANCELLED, DRIVER_CANCELLED));
        graph.put(DRIVER_ACCEPTED, EnumSet.of(DRIVER_ARRIVED, CANCELLED, DRIVER_CANCELLED));
        graph.put(DRIVER_ARRIVED, EnumSet.of(RIDE_STARTED, CANCELLED, DRIVER_CANCELLED));
        graph.put(RIDE_STARTED, EnumSet.of(RIDE_COMPLETED));
        graph.put(RIDE_COMPLETED, EnumSet.of(PAYMENT_PENDING));
        graph.put(PAYMENT_PENDING, EnumSet.of(PAYMENT_COMPLETED));
        graph.put(PAYMENT_COMPLETED, EnumSet.of(RATED));
        graph.put(RATED, EnumSet.noneOf(RideStatus.class));
        graph.put(CANCELLED, EnumSet.noneOf(RideStatus.class));
        graph.put(DRIVER_CANCELLED, EnumSet.of(SEARCHING_DRIVER));
        return graph;
    }

    /** @throws InvalidRideStateException if {@code to} is not a legal next state from {@code from} */
    public void validateTransition(RideStatus from, RideStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidRideStateException(
                    "Cannot move ride from " + from + " to " + to);
        }
    }

    public boolean canTransition(RideStatus from, RideStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean isTerminal(RideStatus status) {
        return TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }
}
