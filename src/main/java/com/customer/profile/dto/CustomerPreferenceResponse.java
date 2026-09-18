package com.customer.profile.dto;

import com.customer.profile.enums.CustomerSize;

public record CustomerPreferenceResponse(
    Long preferenceId,
    CustomerSize size
) {

}
