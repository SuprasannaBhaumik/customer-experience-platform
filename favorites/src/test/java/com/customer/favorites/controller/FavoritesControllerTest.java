package com.customer.favorites.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.customer.favorites.dto.FavoritesDTO;
import com.customer.favorites.service.FavoritesService;

@WebMvcTest 
public class FavoritesControllerTest {

    /* POST returns 201
duplicate returns 409
missing favorite returns 404
DELETE returns 204 */


    @Autowired 
    private MockMvc mockMvc;

    @MockitoBean 
    private FavoritesService service;

    @Test 
    public void shouldCreateFavorite() throws Exception {

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        FavoritesDTO favoritesDTO = 
            new FavoritesDTO(
                12L, 
                customerId, 
                productId, 
                Instant.now()
            );

        when(
            service
            .saveFavorite(
                any(UUID.class), 
                any(UUID.class)
            )
        ).thenReturn(favoritesDTO);

        mockMvc
        .perform(
            post(
                "/api/v1/customers/{customerId}/favorites/{productId}", 
                customerId, 
                productId
            )            
            .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isCreated());

    }


    @Test 
    public void deleteSuccessfull() throws Exception {
        //DELETE returns 204

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        mockMvc
        .perform(delete(
            "/api/v1/customers/{customerId}/favorites/{productId}",
                customerId,
                productId
            )
        )
        .andExpect(status().isNoContent());

        verify(service).deleteFavoriteOfCustomerByProduct(customerId, productId);

    }

}
