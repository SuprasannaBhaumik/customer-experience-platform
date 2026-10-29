package com.customer.payment.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.payment.dto.PaymentResponse;
import com.customer.payment.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping
    public List<PaymentResponse> getPayments(@RequestHeader("X-Authenticated-User") String customerId) {
        return paymentService.getPayments(UUID.fromString(customerId));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(
        @RequestHeader("X-Authenticated-User") String customerId,
        @PathVariable UUID paymentId) {

            return paymentService.getPayment(UUID.fromString(customerId), paymentId);
    }

}
