package com.customer.profile.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ActiveProfiles;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.entity.CustomerPreferences;
import com.customer.profile.entity.CustomerProfile;
import com.customer.profile.enums.CustomerSize;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("local")
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


    @Test 
    void shouldPersistProfileWithPreferences() {
    
        CustomerProfile customer = new CustomerProfile("Suprasnana", "bhaumik", "s.b@gmail.com");
        customer.addPreference(new CustomerPreferences(CustomerSize.L));
        customer.addPreference(new CustomerPreferences(CustomerSize.XXL));
        repository.saveAndFlush(customer);

        CustomerProfile customerFromDB = repository.findByEmail("s.b@gmail.com").get();

        assertEquals(customer.getFirstName(), customerFromDB.getFirstName());
        assertEquals(customer.getPreferences().size(), customerFromDB.getPreferences().size());

    }

}
