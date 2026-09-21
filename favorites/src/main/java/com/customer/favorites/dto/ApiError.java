package com.customer.favorites.dto;

import java.time.Instant;

public record ApiError(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path
) {

}
