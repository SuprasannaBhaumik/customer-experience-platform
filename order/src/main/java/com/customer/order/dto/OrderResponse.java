package com.customer.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.customer.order.domain.OrderItem;
import com.customer.order.domain.OrderStatus;

public record OrderResponse(
    UUID orderId,
    UUID customerId,
    OrderStatus status,
    String shippingAddress,
    BigDecimal totalAmount,
    Instant createdAt,
    Instant updatedAt,
    List<OrderItem> orderItems
) {}
