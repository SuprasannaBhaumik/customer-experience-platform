package com.customer.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter 
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index (name="idx_payment_order_id", columnList = "order_id"),
        @Index (name="idx_payment_customer_id", columnList = "customer_id")
    }
)
@NoArgsConstructor
public class Payment {
    public Payment(UUID paymentId, UUID orderId, UUID customerId,
            BigDecimal amount, String currency, PaymentStatus processing,
            String paymentMethodId, Instant createdAt, Instant updatedAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = processing;
        this.paymentMethodId = paymentMethodId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID paymentId;

    @Column(name="order_id", nullable = false)
    private UUID orderId;

    @Column(name="customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "payment_method_id", nullable = false)
    private String paymentMethodId;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

}
