package com.customer.profile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.dto.ProfileResponse;
import com.customer.profile.service.ProfileService;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ProfileController.class)
public class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateProfile() throws Exception {

        UUID customerId = UUID.randomUUID();

        CreateProfileRequest request = new CreateProfileRequest(
                "sam",
                "brown",
                "s.b@gmail.com");

        ProfileResponse response = new ProfileResponse(customerId, "sam", "brown", "s.b@gmail.com", null);

        when(profileService.saveProfile(any(CreateProfileRequest.class))).thenReturn(response);

        mockMvc.perform(
                    post("/api/v1/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated());

    }

    @Test
    void createProfileWithEmptyUserName() throws Exception {

        CreateProfileRequest request = new CreateProfileRequest(
                "     ",
                "b",
                "s.b@gmail.com");

        mockMvc.perform(
                    post("/api/v1/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.apiError.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

}
