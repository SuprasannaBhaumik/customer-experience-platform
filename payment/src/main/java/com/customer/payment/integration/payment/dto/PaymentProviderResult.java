package com.customer.payment.integration.payment.dto;

public record PaymentProviderResult(

    PaymentProviderStatus status,

    String providerReference,

    String failureCode,

    String failureMessage
) {

}
