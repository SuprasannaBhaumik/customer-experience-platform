package com.customer.payment.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String key) {
        super("The duplicate key is used for the payment -> " + key);
    }

}
