package com.customer.favorites.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.customer.favorites.domain.Favorites;
import com.customer.favorites.dto.FavoritesDTO;
import com.customer.favorites.exception.DuplicateFavoriteException;
import com.customer.favorites.exception.NoProductFoundForCustomerException;
import com.customer.favorites.repository.FavoritesRepository;

@ExtendWith(MockitoExtension.class)
public class FavoritesServiceTest {

    @InjectMocks  
    private FavoritesService service;

    @Mock 
    private FavoritesRepository repository;


    @Test 
    public void shouldAddFavorite() {

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(repository.save(any(Favorites.class)))
        .thenAnswer( invocation -> invocation.getArgument(0));

        FavoritesDTO response = service.saveFavorite(customerId, productId);

        assertEquals(customerId, response.customerId());
        assertEquals(productId, response.productId());

    }

    @Test 
    public void shouldRejectDuplicateFavorite() {

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(repository.existsByCustomerIdAndProductId(customerId, productId))
                .thenReturn(true);

        assertThrows(
                DuplicateFavoriteException.class,
                () -> service.saveFavorite(customerId, productId)
        );

        verify(repository, never()).save(any(Favorites.class));
    }

    @Test 
    public void shouldReturnFavoritesForCustomer() {
        UUID customerId = UUID.randomUUID();
        Favorites firstFavorite = new Favorites(customerId, UUID.randomUUID(), Instant.now());
        Favorites secondFavorite = new Favorites(customerId, UUID.randomUUID(), Instant.now());

        when(repository.findByCustomerId(customerId))
                .thenReturn(List.of(firstFavorite, secondFavorite));

        List<FavoritesDTO> response = service.listAllFavoritesOfACustomer(customerId);

        assertEquals(2, response.size());
        assertEquals(firstFavorite.getProductId(), response.get(0).productId());
        assertEquals(secondFavorite.getProductId(), response.get(1).productId());
        verify(repository).findByCustomerId(customerId);

    }

    @Test 
    public void shouldReturnSingleFavorite() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Favorites favorite = new Favorites(customerId, productId, createdAt);

        when(repository.findByCustomerId(customerId)).thenReturn(List.of(favorite));

        List<FavoritesDTO> response = service.listAllFavoritesOfACustomer(customerId);

        assertEquals(1, response.size());
        assertEquals(customerId, response.get(0).customerId());
        assertEquals(productId, response.get(0).productId());
        assertEquals(createdAt, response.get(0).createdAt());
    }

    @Test 
    public void shouldThrowWhenFavoriteMissing() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(repository.existsByCustomerIdAndProductId(any(UUID.class), any(UUID.class)))
        .thenReturn(false);

        assertThrows(
                NoProductFoundForCustomerException.class,
                () -> service.deleteFavoriteOfCustomerByProduct(customerId, productId)
        );

        verify(repository, never()).deleteByCustomerIdAndProductId(customerId, productId);
    }

    @Test 
    public void shouldDeleteFavorite() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(repository.existsByCustomerIdAndProductId(customerId, productId))
                .thenReturn(true);

        service.deleteFavoriteOfCustomerByProduct(customerId, productId);

        verify(repository).deleteByCustomerIdAndProductId(customerId, productId);
    }



}
