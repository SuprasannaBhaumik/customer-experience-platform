package com.customer.payment.integration.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentProviderRequest(

    UUID paymentId,

    BigDecimal amount,

    String currency,

    String paymentMethodId,

    String idempotencyKey) {

}
