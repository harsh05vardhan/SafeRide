package com.saferide.payment.dto;

import com.saferide.payment.PaymentStatus;
import java.time.Instant;

public record PaymentResponse(
        Long rideId,
        double amount,
        String currency,
        PaymentStatus status,
        String transactionReference,
        Instant paidAt) {
}
