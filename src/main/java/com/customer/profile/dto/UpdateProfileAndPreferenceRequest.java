package com.customer.profile.dto;

import java.util.List;

public record UpdateProfileAndPreferenceRequest(
    List<PreferenceRequest> preferenceRequests,
    UpdateProfileRequest profileRequest
) {

}
