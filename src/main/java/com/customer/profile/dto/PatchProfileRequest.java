package com.customer.profile.dto;

public record PatchProfileRequest(
    String firstName,
    String lastName,
    String email
) {

}
