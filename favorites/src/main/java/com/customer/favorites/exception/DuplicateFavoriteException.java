package com.customer.favorites.exception;

import java.util.UUID;

public class DuplicateFavoriteException extends RuntimeException {

    public DuplicateFavoriteException(UUID customerId, UUID productId) {
        super("Favorited product: " + productId + " already exists for this customer: "+ customerId);
    }

}
