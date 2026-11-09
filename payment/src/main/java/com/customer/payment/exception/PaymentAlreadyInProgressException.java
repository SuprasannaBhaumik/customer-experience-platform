package com.customer.payment.exception;

public class PaymentAlreadyInProgressException extends RuntimeException {

    public PaymentAlreadyInProgressException(String key) {
        super("The key is used for an ongoing payment -> " + key);
    }
}
