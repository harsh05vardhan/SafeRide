package com.saferide.payment;

import com.saferide.exception.PaymentFailedException;
import com.saferide.exception.ResourceNotFoundException;
import com.saferide.fare.FareService;
import com.saferide.fare.dto.RideFareResponse;
import com.saferide.payment.dto.PaymentResponse;
import com.saferide.ride.Ride;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@link Payment} persistence and the one call out to
 * {@link PaymentGateway}. Deliberately never accepts an amount from a
 * caller - always reads the ride's currently-locked fare itself via
 * {@link FareService}, so "what gets charged" can never diverge from
 * "what the customer confirmed" (see the Fare Lock guarantee). Ride-status
 * transitions are {@code RideService}'s job, not this class's - same
 * one-directional dependency shape {@code FareService} already
 * establishes for the same reason.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final FareService fareService;

    /**
     * Charges the ride's locked fare. Called exactly once per ride, by
     * {@code RideService.payForRide} as it moves {@code PAYMENT_PENDING ->
     * PAYMENT_COMPLETED} - {@code RideStateMachine} has no edge back into
     * {@code PAYMENT_PENDING}, so a second call for the same ride can never happen.
     */
    @Transactional
    public PaymentResponse pay(Ride ride) {
        RideFareResponse fare = fareService.getRideFare(ride.getId());
        double amount = fare.lockedAmount() != null ? fare.lockedAmount() : fare.totalFare();

        PaymentChargeResult result = paymentGateway.charge(ride.getId(), amount, fare.currency());

        Payment payment = new Payment();
        payment.setRide(ride);
        payment.setAmount(amount);
        payment.setCurrency(fare.currency());

        if (!result.success()) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new PaymentFailedException(
                    result.failureReason() != null ? result.failureReason() : "Payment could not be processed");
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionReference(result.transactionReference());
        payment.setPaidAt(Instant.now());
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getForRide(Long rideId) {
        Payment payment = paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for ride " + rideId));
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getRide().getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getTransactionReference(),
                payment.getPaidAt());
    }
}
