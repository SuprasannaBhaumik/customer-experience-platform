package com.customer.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

    @NotBlank(message = "Name cannot be blank") 
    @Size(min = 2, max = 50, message = "Please enter first name between 2 to 50 characters")
    String firstName,

    @NotBlank(message = "Name cannot be blank") 
    @Size(min = 2, max = 50, message = "Please enter last name between 2 to 50 characters")
    String lastName,

    @NotBlank
    @Email
    String email
) {

}
