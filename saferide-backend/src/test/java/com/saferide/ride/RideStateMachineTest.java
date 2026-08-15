package com.saferide.ride;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saferide.exception.InvalidRideStateException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class RideStateMachineTest {

    private final RideStateMachine stateMachine = new RideStateMachine();

    @ParameterizedTest
    @CsvSource({
        "REQUESTED, SEARCHING_DRIVER",
        "SEARCHING_DRIVER, DRIVER_ASSIGNED",
        "DRIVER_ASSIGNED, DRIVER_ACCEPTED",
        "DRIVER_ACCEPTED, DRIVER_ARRIVED",
        "DRIVER_ARRIVED, RIDE_STARTED",
        "RIDE_STARTED, RIDE_COMPLETED",
        "RIDE_COMPLETED, PAYMENT_PENDING",
        "PAYMENT_PENDING, PAYMENT_COMPLETED",
        "PAYMENT_COMPLETED, RATED",
        "DRIVER_CANCELLED, SEARCHING_DRIVER",
    })
    void allowsEveryTransitionInTheHappyPathAndReassignmentFlow(RideStatus from, RideStatus to) {
        assertThat(stateMachine.canTransition(from, to)).isTrue();
        assertThatCode(() -> stateMachine.validateTransition(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "REQUESTED, CANCELLED",
        "SEARCHING_DRIVER, CANCELLED",
        "DRIVER_ASSIGNED, CANCELLED",
        "DRIVER_ASSIGNED, DRIVER_CANCELLED",
        "DRIVER_ACCEPTED, CANCELLED",
        "DRIVER_ACCEPTED, DRIVER_CANCELLED",
        "DRIVER_ARRIVED, CANCELLED",
        "DRIVER_ARRIVED, DRIVER_CANCELLED",
    })
    void allowsCancellationFromEveryPreRideState(RideStatus from, RideStatus to) {
        assertThat(stateMachine.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "REQUESTED, DRIVER_ASSIGNED",         // can't skip SEARCHING_DRIVER
        "SEARCHING_DRIVER, DRIVER_ACCEPTED",  // can't skip DRIVER_ASSIGNED
        "DRIVER_ACCEPTED, RIDE_STARTED",      // can't skip DRIVER_ARRIVED - no OTP would ever be issued
        "DRIVER_ARRIVED, DRIVER_ACCEPTED",    // no going backwards
        "RIDE_STARTED, CANCELLED",            // a ride in progress cannot be cancelled, only completed
        "RIDE_STARTED, DRIVER_CANCELLED",
        "RIDE_COMPLETED, CANCELLED",
        "PAYMENT_PENDING, RIDE_STARTED",
        "CANCELLED, SEARCHING_DRIVER",        // terminal
        "RATED, PAYMENT_COMPLETED",           // terminal
    })
    void rejectsIllegalTransitions(RideStatus from, RideStatus to) {
        assertThat(stateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidRideStateException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"RATED", "CANCELLED"})
    void ratedAndCancelledAreTerminal(RideStatus terminal) {
        assertThat(stateMachine.isTerminal(terminal)).isTrue();
    }

    @Test
    void driverCancelledIsNotTerminalBecauseReassignmentCanReopenIt() {
        assertThat(stateMachine.isTerminal(RideStatus.DRIVER_CANCELLED)).isFalse();
    }

    @Test
    void everyNonTerminalStatusHasAtLeastOneOutgoingTransition() {
        for (RideStatus status : RideStatus.values()) {
            if (!stateMachine.isTerminal(status)) {
                boolean hasSomeOutgoingEdge = Arrays.stream(RideStatus.values())
                        .anyMatch(candidate -> stateMachine.canTransition(status, candidate));
                assertThat(hasSomeOutgoingEdge).as("status %s should have an outgoing edge", status).isTrue();
            }
        }
    }
}
