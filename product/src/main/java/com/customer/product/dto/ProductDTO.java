package com.customer.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductDTO(
    UUID productId,
    String sku,
    String name,
    String description,
    BigDecimal price,
    InventoryStatus inventoryStatus
) {

}
