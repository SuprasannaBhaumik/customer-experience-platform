package com.customer.payment.domain;

/**
 * PENDING
    accepted but not started
PROCESSING
    provider call is in progress: e.g Aurus
SUCCEEDED
    provider confirmed success
FAILED
    provider confirmed failure
UNKNOWN
    provider outcome is ambiguous
 * 
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
