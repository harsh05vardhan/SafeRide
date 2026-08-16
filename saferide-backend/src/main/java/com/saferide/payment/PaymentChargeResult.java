package com.saferide.payment;

/** What a {@link PaymentGateway} hands back for one charge attempt. */
public record PaymentChargeResult(boolean success, String transactionReference, String failureReason) {

    public static PaymentChargeResult success(String transactionReference) {
        return new PaymentChargeResult(true, transactionReference, null);
    }

    public static PaymentChargeResult failure(String reason) {
        return new PaymentChargeResult(false, null, reason);
    }
}
