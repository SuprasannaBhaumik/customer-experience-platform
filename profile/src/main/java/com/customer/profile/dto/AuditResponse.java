package com.customer.profile.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditResponse(
    Long auditId, 
    UUID customerId, 
    Instant createdTime, 
    String activityPerformed) {

}
