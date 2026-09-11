package com.customer.profile.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.dto.PatchProfileRequest;
import com.customer.profile.dto.ProfileResponse;
import com.customer.profile.dto.UpdateProfileRequest;
import com.customer.profile.service.ProfileService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;

@RestController
@RequestMapping ("/api/v1/profiles")
@Validated 
public class ProfileController {

    @Autowired 
    private ProfileService profileService;

    //using @valid here enforces the jakarta annotations we applied in the request
    @PostMapping
    public ResponseEntity<ProfileResponse> createProfile(@RequestBody @Valid CreateProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.saveProfile(request));
    }

    @GetMapping (value = "/{customerId}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable @Valid UUID customerId) {
        return ResponseEntity.ok(profileService.getProfile(customerId));
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfileByEmail(@RequestParam @Email String email) {
        return ResponseEntity.ok(profileService.findProfileByEmail(email));
    }

    @DeleteMapping(value="/{customerId}")
    public ResponseEntity<String> deleteByCustomerId(@PathVariable UUID customerId) {
        String response = profileService.deleteProfileByCustomerId(customerId);
        if( response.equalsIgnoreCase("success")) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
    }

    @PutMapping(value="/{customerId}")
    public ResponseEntity<String> updateCustomer(@PathVariable UUID customerId, @RequestBody @Valid UpdateProfileRequest request) {
        String response = profileService.updateCustomerProfile(customerId, request);
        if( response.equalsIgnoreCase("success")) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @PatchMapping(value="/{customerId}")
    public ResponseEntity<String> patchCustomer(@PathVariable UUID customerId, @RequestBody PatchProfileRequest request) {

        String response = profileService.patchCustomerProfile(customerId, request);
        if (response.equalsIgnoreCase("success")) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }


}
