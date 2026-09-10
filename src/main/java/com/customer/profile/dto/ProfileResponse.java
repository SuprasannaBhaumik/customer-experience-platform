package com.customer.profile.dto;

import java.util.UUID;

public record ProfileResponse(UUID id, String firstName, String lastName, String email) {

}
