package com.customer.order.exception;

import java.util.UUID;

public class ProductNotAvailableForOrderException extends RuntimeException {

    public ProductNotAvailableForOrderException(UUID productId) { super("Order is not able to validate the Product: " + productId); }

}
