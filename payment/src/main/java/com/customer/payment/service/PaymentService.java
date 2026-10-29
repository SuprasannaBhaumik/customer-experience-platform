package com.customer.payment.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.payment.domain.Payment;
import com.customer.payment.dto.PaymentResponse;
import com.customer.payment.exception.PaymentNotFoundException;
import com.customer.payment.repository.PaymentRepository;

@Service
@Transactional
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(UUID customerId) {
        return paymentRepository.findByCustomerId(customerId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID customerId, UUID paymentId) {
        return paymentRepository.findByPaymentIdAndCustomerId(paymentId, customerId)
            .map(this::toResponse)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getCreatedAt()
        );
    }
}
