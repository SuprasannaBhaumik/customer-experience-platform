package com.customer.order.exception;

public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException() {
        super("Product service unreachable");
    }

}
