package com.customer.favorites.exception;

import java.util.UUID;

public class NoProductFoundForCustomerException extends RuntimeException {

    
    public NoProductFoundForCustomerException(UUID customerId, UUID productId) {
        super("No Favorited Products (bearing productId: "+ productId + ") found for this customer: "+ customerId);
    }
}
