package com.saferide.payment;

/** {@code MockPaymentGateway} always reaches COMPLETED - FAILED exists for a real gateway's benefit later, not exercised today. */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
