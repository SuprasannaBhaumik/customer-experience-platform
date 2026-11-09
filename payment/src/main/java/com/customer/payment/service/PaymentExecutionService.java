package com.customer.payment.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.customer.payment.domain.Payment;
import com.customer.payment.dto.CreatePaymentRequest;
import com.customer.payment.dto.PaymentResponse;
import com.customer.payment.integration.payment.PaymentProviderClient;
import com.customer.payment.integration.payment.dto.PaymentProviderRequest;
import com.customer.payment.integration.payment.dto.PaymentProviderResult;

@Service 
public class PaymentExecutionService {

    @Autowired 
    private PaymentPersistenceService persistenceService;

    @Autowired 
    private PaymentProviderClient providerClient;

    public PaymentResponse execute(String idempotencyKey, CreatePaymentRequest request) {

        Payment payment = persistenceService.createProcessing(request);

        PaymentProviderResult result = 
            providerClient.charge(
                new PaymentProviderRequest(
                    payment.getPaymentId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getPaymentMethodId(),
                    idempotencyKey
                )
            );
        
        return switch (
            result.status()
        ) {

            case SUCCEEDED ->
                persistenceService.markSucceeded(
                    payment.getPaymentId(),
                    result.providerReference()
                );

            case DECLINED ->
                persistenceService.markFailed(
                    payment.getPaymentId(),
                    result.failureCode(),
                    result.failureMessage()
                );

            case UNKNOWN ->
                persistenceService.markUnknown(
                    payment.getPaymentId(),
                    result.failureMessage()
                );
        };
    }
}
