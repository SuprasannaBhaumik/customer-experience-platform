package com.customer.payment.exception;

public class PreviousPaymentAttemptFailedException extends RuntimeException {

    public PreviousPaymentAttemptFailedException(String key) {
        super("The key used for payment had a failure -> " + key);
    }

}
