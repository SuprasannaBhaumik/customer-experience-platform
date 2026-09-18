package com.customer.profile.dto;

import java.util.List;
import java.util.UUID;

public record ProfileResponse(
    UUID id, 
    String firstName, 
    String lastName, 
    String email,
    List<CustomerPreferenceResponse> preferences
) {

}
