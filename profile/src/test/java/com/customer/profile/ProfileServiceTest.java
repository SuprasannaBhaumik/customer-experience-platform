package com.customer.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.dto.ProfileResponse;
import com.customer.profile.entity.CustomerProfile;
import com.customer.profile.repository.ProfileRepository;
import com.customer.profile.service.ProfileService;

@ExtendWith(MockitoExtension.class)
public class ProfileServiceTest {

    @InjectMocks 
    private ProfileService profileService;

    @Mock 
    private ProfileRepository profileRepository;

    @Spy 
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test 
    void shouldCreateProfile() {
        CreateProfileRequest cpr = new CreateProfileRequest("Suprasanna", "Bhaumik", "suprasanna.bhaumik86@gmail.com",
                "suprasanna", "password123");

        when(profileRepository.save(any(CustomerProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse pr = profileService.saveProfile(cpr);

        assertNotNull(pr.firstName());
        assertEquals("Suprasanna", pr.firstName());
        assertEquals("suprasanna", pr.username());

        verify(profileRepository, times(1)).save(any(CustomerProfile.class));
    }

}
