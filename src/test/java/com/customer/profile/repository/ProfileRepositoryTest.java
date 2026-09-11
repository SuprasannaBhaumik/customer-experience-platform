package com.customer.profile.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.entity.CustomerProfile;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public class ProfileRepositoryTest {

    @Autowired 
    ProfileRepository repository;

    @Test 
    void shouldCreateProfile() {

        CreateProfileRequest profileRequest = 
            new CreateProfileRequest(
                "suprasanna", 
                "bhaumik", 
                "suprasanna.bhaumik86@gmail.com"
            );
        
        repository.save(new CustomerProfile(profileRequest.firstName(), profileRequest.lastName(), profileRequest.email()));

        Optional<CustomerProfile> customerProfile = repository.findByEmail(profileRequest.email());
        
        assertTrue(customerProfile.isPresent());
        assertEquals(customerProfile.get().getEmail(), profileRequest.email());

    }



}
