package com.customer.profile.dto;

public record UpdateProfileRequest(
    String firstName,
    String lastName,
    String email
) {

}
