package com.customer.profile.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.dto.ProfileResponse;
import com.customer.profile.service.ProfileService;

@RestController
@RequestMapping ("/api/v1/profiles")
public class ProfileController {

    @Autowired 
    private ProfileService profileService;

    @PostMapping
    public ProfileResponse createProfile(@RequestBody CreateProfileRequest request) {
        return profileService.saveProfile(request);
    }

    @GetMapping (value = "/{customerId}")
    public ProfileResponse getProfile(@PathVariable UUID customerId) {
        return profileService.getProfile(customerId);
    }

}
