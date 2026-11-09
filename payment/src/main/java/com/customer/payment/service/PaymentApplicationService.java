package com.customer.payment.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.customer.payment.domain.IdempotencyDecisionType;
import com.customer.payment.dto.CreatePaymentRequest;
import com.customer.payment.dto.IdempotencyDecision;
import com.customer.payment.dto.PaymentResponse;
import com.customer.payment.util.IdempotencyKeyValidator;
import com.customer.payment.util.RequestHashGenerator;

@Service 
public class PaymentApplicationService {

    @Autowired 
    private IdempotencyKeyValidator keyValidator;

    @Autowired 
    private RequestHashGenerator hashGenerator;

    @Autowired 
    private IdempotencyService idempotencyService;

    @Autowired 
    private PaymentExecutionService executionService;

    public PaymentResponse createPayment(String subject, String idempotencyKey, CreatePaymentRequest request) {

        // Validate key
        keyValidator.validate(idempotencyKey);
        
        // Generate hash
        String requestHash = hashGenerator.hash(request);

        // Check for uniqueness of payment - idempotency , get the decision
        // This check is mainly done to ensure the same charge made twice or multiple times return back the cached result
        IdempotencyDecision decision = idempotencyService.checkAndRegisterPayment(subject, idempotencyKey, requestHash);

        if(decision.decisionType() == IdempotencyDecisionType.RETURN_CACHED) {
            return decision.paymentResponse();
        }

        PaymentResponse paymentResponse;

        try {

            paymentResponse = executionService.execute(idempotencyKey, request);

        } catch (RuntimeException exception) {

            idempotencyService.fail(subject, idempotencyKey);
            throw exception;
        }

        idempotencyService.complete(subject, idempotencyKey, paymentResponse);

        return paymentResponse;
    }
}
