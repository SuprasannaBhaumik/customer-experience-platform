package com.customer.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
    @NotNull
    UUID orderId,

    @NotNull
    UUID customerId,

    @NotNull
    @DecimalMin("0.01")
    BigDecimal amount,

    @NotBlank
    @Size(min = 3, max = 3)
    String currency,

    //TODO as next steps this paymentMethodId 
    // will be pulled from our savedCards and processed for payments
    @NotBlank
    String paymentMethodId
) {

}
