package com.customer.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID productId,
        String sku,
        String name,
        BigDecimal price,
        InventoryStatus inventoryStatus
) {
}