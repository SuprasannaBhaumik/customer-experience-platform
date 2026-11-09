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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name="user_tender")
@Getter 
@Setter 
@NoArgsConstructor 
public class Tender {

    @Id 
    @GeneratedValue(strategy=GenerationType.AUTO)
    private UUID id;

    @Column
    private UUID customerId;

    @Column
    private String provider;           // Payment provider or vault

    @Column
    private String paymentMethodId;   // Reference to the card in that vault

    @Column
    private String cardholderName;
    
    @Enumerated(EnumType.STRING)
    private CardNetwork cardNetwork;     // VISA, MASTERCARD, AMEX, etc.
    
    @Column
    private String lastFourDigits;       // String preserves leading zeros
    
    @Column
    private Integer expiryMonth;
    
    @Column
    private Integer expiryYear;

    @Column
    private boolean defaultTender;
    
    @Enumerated(EnumType.STRING)
    private TenderStatus status;         // ACTIVE, EXPIRED, DISABLED

    @Column
    private Instant createdAt;
    
    @Column
    private Instant updatedAt;


}
