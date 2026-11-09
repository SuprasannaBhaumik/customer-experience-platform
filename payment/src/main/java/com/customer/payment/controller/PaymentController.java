package com.customer.payment.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.payment.domain.PaymentStatus;
import com.customer.payment.dto.CreatePaymentRequest;
import com.customer.payment.dto.PaymentResponse;
import com.customer.payment.service.PaymentApplicationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @Autowired 
    private PaymentApplicationService paymentApplicationService;

    @PostMapping 
    public ResponseEntity<PaymentResponse> processPayment(
        @RequestHeader("Idempotency-key") String idempotencyKey, 
        @RequestHeader("X-Autheticated-User") String subject,
        @Valid @RequestBody CreatePaymentRequest paymentRequest
    ) {

        PaymentResponse paymentResponse = 
            paymentApplicationService.createPayment(
                subject, 
                idempotencyKey, 
                paymentRequest
            );
        
        HttpStatus status =
            paymentResponse.status()
                == PaymentStatus.SUCCEEDED
                ? HttpStatus.CREATED
                : HttpStatus.UNPROCESSABLE_ENTITY;
        
        return ResponseEntity
            .status(status)
            .body(paymentResponse);
    }

}
