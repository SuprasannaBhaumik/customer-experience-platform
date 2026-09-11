package com.customer.profile.exception;

import java.util.UUID;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(UUID customerId) {
        super("Profile not found for customer:" + customerId);
    }

}
