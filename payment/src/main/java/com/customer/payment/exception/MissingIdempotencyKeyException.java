package com.customer.payment.exception;

public class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("Key is missing");
    }
}
