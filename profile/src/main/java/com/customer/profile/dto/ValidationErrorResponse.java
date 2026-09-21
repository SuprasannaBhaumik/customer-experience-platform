package com.customer.profile.dto;

import java.util.Map;

import jakarta.validation.Valid;

public record ValidationErrorResponse(
    @Valid ApiError apiError,
    Map<String, String> errors
) {

}
