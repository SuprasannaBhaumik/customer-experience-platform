package com.customer.product.dto;

import java.math.BigDecimal;

public record UpdateProductRequest(
    String name,
    BigDecimal price,
    InventoryStatus status
) {

}
