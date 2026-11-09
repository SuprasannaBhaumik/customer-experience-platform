package com.customer.payment.util;

import org.springframework.stereotype.Component;

import com.customer.payment.exception.InvalidIdempotencyKeyException;
import com.customer.payment.exception.MissingIdempotencyKeyException;

@Component 
public class IdempotencyKeyValidator {

    public void validate(String key) {
        if(key == null || key.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }

        if(key.length() > 200) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key is too long");
        }
    }
}
