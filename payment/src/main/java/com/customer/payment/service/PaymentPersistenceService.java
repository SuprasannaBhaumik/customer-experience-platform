package com.customer.payment.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.customer.payment.domain.Payment;
import com.customer.payment.domain.PaymentStatus;
import com.customer.payment.dto.CreatePaymentRequest;
import com.customer.payment.dto.PaymentResponse;
import com.customer.payment.exception.PaymentNotFoundException;
import com.customer.payment.repository.PaymentRepository;

import jakarta.transaction.Transactional;

/**
 * This service deals with saving the status of the payment once 
 * it is returned back from the payment provider
 * PaymentPersistenceService
 */
@Service 
public class PaymentPersistenceService {

    @Autowired 
    private PaymentRepository paymentRepository;

    @Transactional
    public Payment createProcessing(CreatePaymentRequest request) {

        Instant now = Instant.now();

        Payment payment = new Payment(
                UUID.randomUUID(),
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency().toUpperCase(Locale.ROOT),
                PaymentStatus.PROCESSING,
                request.paymentMethodId(),
                now,
                now);

        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentResponse markSucceeded(UUID paymentId, String providerReference) {

        Payment payment = get(paymentId);

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderReference(providerReference);
        payment.setUpdatedAt(Instant.now());

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse markFailed(UUID paymentId, String failureCode, String failureMessage) {

        Payment payment = get(paymentId);

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureCode(failureCode);
        payment.setFailureMessage(failureMessage);
        payment.setUpdatedAt(Instant.now());

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse markUnknown(UUID paymentId, String failureMessage) {

        Payment payment = get(paymentId);

        payment.setStatus(PaymentStatus.UNKNOWN);
        payment.setFailureCode("UNKNOWN");
        payment.setFailureMessage(failureMessage);
        payment.setUpdatedAt(Instant.now());

        return toResponse(payment);
    }
    private Payment get(UUID paymentId) {
        return paymentRepository
            .findById(paymentId)
            .orElseThrow( () -> new PaymentNotFoundException(paymentId));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getOrderId(),
            payment.getCustomerId(), 
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getProviderReference(), 
            payment.getFailureCode(), 
            payment.getCreatedAt()
        );
    }
}
