package com.saferide.exception;

import org.springframework.http.HttpStatus;

/** Thrown when {@code PaymentGateway.charge} reports failure - never actually reachable via {@code MockPaymentGateway}, which always succeeds, but real gateways fail. */
public class PaymentFailedException extends ApiException {

    public PaymentFailedException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED");
    }
}
