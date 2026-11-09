package com.customer.payment.dto;

import com.customer.payment.domain.IdempotencyDecisionType;

public record IdempotencyDecision(
    IdempotencyDecisionType decisionType, 
    PaymentResponse paymentResponse
) {

    public static IdempotencyDecision execute() {
        return new IdempotencyDecision(IdempotencyDecisionType.EXECUTE, null);
    }

    public static IdempotencyDecision cached(PaymentResponse response) {
        return new IdempotencyDecision(IdempotencyDecisionType.RETURN_CACHED, response);
    }

}
