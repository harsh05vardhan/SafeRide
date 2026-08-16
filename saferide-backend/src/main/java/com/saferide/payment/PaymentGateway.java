package com.saferide.payment;

/**
 * The one seam between SafeRide and whatever actually moves money -
 * exactly {@code MapService}'s relationship to a real routing provider.
 * {@link MockPaymentGateway} is the only implementation today; a real
 * provider (Razorpay, Stripe) becomes a second {@code @Service}
 * implementation later - {@link PaymentService} and everything above it
 * never changes.
 */
public interface PaymentGateway {

    /** Charges the given amount for a ride. Never trusts an amount the caller didn't itself derive from the ride's locked fare. */
    PaymentChargeResult charge(Long rideId, double amount, String currency);
}
