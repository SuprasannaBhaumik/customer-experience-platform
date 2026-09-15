package com.customer.profile.dto;

import com.customer.profile.enums.CustomerSize;

public record CustomerPreferenceResponse(
    int preferenceId,
    CustomerSize size
) {

}
