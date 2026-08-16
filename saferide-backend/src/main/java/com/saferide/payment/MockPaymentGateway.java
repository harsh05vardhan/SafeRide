package com.saferide.payment;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Development stand-in for a real payment provider - always succeeds,
 * instantly, with a fake but unique transaction reference. Deterministic
 * and offline, exactly like {@code MockMapService}: no API key, no
 * network call, no card details ever handled. Swapping in a real gateway
 * later means adding a second {@link PaymentGateway} implementation;
 * {@link PaymentService} and every caller above it is unaffected.
 */
@Service
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentChargeResult charge(Long rideId, double amount, String currency) {
        return PaymentChargeResult.success("MOCK-" + UUID.randomUUID());
    }
}
