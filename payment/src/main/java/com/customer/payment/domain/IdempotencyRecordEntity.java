package com.customer.payment.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(
    name="idempotency_record",
    uniqueConstraints = {
        @UniqueConstraint(
            name= "uk_idempotency_subject_key", 
            columnNames = {
                "subject",
                "idempotency_key"
            }
        )
    }
)
@Getter 
@Setter 
@NoArgsConstructor 
public class IdempotencyRecordEntity {

    public IdempotencyRecordEntity(String subject, String key, String requestHash, IdempotencyStatus inProgress,
            Instant now) {
        this.subject = subject;
        this.idempotencyKey = key;
        this.requestHash = requestHash;
        this.status = inProgress;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(
        name = "idempotency_key",
        nullable = false,
        length = 200
    )
    private String idempotencyKey;

    @Column(
        name = "request_hash",
        nullable = false,
        length = 64
    )
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

     @Column(
        name = "payment_id"
    )
    private UUID paymentId;

    @Column(
        name = "response_code"
    )
    private Integer responseCode;

    @Column(
        name = "response_body",
        columnDefinition = "text"
    )
    private String responseBody;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

}
