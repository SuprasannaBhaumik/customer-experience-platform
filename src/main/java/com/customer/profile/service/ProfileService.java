package com.customer.profile.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.dto.ProfileResponse;
import com.customer.profile.entity.CustomerProfile;
import com.customer.profile.repository.ProfileRepository;

@Service 
public class ProfileService {

    @Autowired
    @Qualifier("myChoosenRepo")
    private ProfileRepository repository;


    public ProfileResponse saveProfile(CreateProfileRequest request) {
        CustomerProfile profile = repository.save(new CustomerProfile( request.firstName(), request.lastName(), request.email()));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(), profile.getEmail());
    }

    public ProfileResponse getProfile(UUID customerId) {
        CustomerProfile profile = repository.findById(customerId).orElseThrow(() -> new RuntimeException("Profile not found"));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(), profile.getEmail());
    }

}
