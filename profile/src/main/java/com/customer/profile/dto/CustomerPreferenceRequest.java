package com.customer.profile.dto;

import com.customer.profile.enums.CustomerSize;

import jakarta.validation.constraints.NotNull;

public record CustomerPreferenceRequest(
    @NotNull
    CustomerSize size
) {
}
