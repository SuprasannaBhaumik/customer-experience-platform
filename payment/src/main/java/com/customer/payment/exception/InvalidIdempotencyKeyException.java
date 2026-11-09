package com.customer.payment.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }

}
