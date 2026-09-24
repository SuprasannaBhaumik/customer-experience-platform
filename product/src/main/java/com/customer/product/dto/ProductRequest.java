package com.customer.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductRequest(
    @NotBlank
    String sku,

    @NotBlank
    String name,

    String description,

    @NotNull
    @PositiveOrZero
    BigDecimal price,

    @NotNull
    InventoryStatus inventoryStatus
) {

}
