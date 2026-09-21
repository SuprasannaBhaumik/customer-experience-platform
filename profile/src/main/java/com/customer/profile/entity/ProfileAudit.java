package com.customer.profile.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected ProfileAudit() {
        //required for JPA
    }

    public ProfileAudit(UUID customerId, String activityPerformed, Instant createdTime) {
        this.activity = activityPerformed;
        this.createdTime = createdTime;
        this.custmomerId = customerId;
    }

    public Long getId() {
        return this.id;
    }

    public String getActivity() {
        return this.activity;
    }

    public Instant getCreatedTime() {
        return this.createdTime;
    }

    public UUID getCustomerId() {
        return this.custmomerId;
    }

    public void setCreatedTime( Instant time) {
        this.createdTime = time;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public void setCustomerId(UUID customerId) {
        this.custmomerId = customerId;
    }

}
