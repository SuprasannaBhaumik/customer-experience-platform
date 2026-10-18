package com.customer.profile.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity 
@Table(name = "PROFILE_AUDIT")
public class ProfileAudit {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="customer_profile_id", nullable = false)
    private UUID custmomerId;

    @Column(name="created_time")
    private Instant createdTime;

    @Column (name="activity_performed", nullable = false)
    private String activity;

    public ProfileAudit(UUID customerId, String activityPerformed, Instant createdTime) {
        this.activity = activityPerformed;
        this.createdTime = createdTime;
        this.custmomerId = customerId;
    }

    public UUID getCustomerId() {
        return this.custmomerId;
    }

    public void setCustomerId(UUID customerId) {
        this.custmomerId = customerId;
    }

}
