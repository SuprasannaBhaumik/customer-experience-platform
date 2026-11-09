package com.customer.payment.domain;

public enum IdempotencyStatus {

    //one request currently owns execution
    IN_PROGRESS,

    //operation completed and stored response exists
    COMPLETED, 

    //previous attempt ended in a known failure state
    FAILED 
}
