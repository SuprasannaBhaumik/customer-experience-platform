package com.customer.profile.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditRequest(
    UUID customerId, 
    String activityPerformed, 
    Instant createdTime) {

}
