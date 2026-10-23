package com.customer.order.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(@NotNull UUID orderId) {}
