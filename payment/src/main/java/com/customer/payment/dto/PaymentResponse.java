package com.customer.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.customer.payment.domain.PaymentStatus;

public record PaymentResponse(
    UUID paymentId,
    UUID orderId,
    UUID customerId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String providerReference,
    String failureCode,
    Instant createdAt
) {}
