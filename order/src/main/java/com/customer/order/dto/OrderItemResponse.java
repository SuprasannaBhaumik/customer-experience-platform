package com.customer.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
    UUID orderItemId, 
    UUID productId, 
    String productName,
    int quantity, 
    BigDecimal unitPrice, 
    BigDecimal lineTotal,
    InventoryStatus inventoryStatus
) {
}
