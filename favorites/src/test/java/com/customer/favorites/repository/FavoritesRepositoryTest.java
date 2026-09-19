package com.customer.favorites.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import com.customer.favorites.domain.Favorites;
import com.customer.favorites.exception.DuplicateFavoriteException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public class FavoritesRepositoryTest {

    @Autowired 
    private FavoritesRepository repository;

    @Test 
    public void test_findByCustomerId() {

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Favorites myFavorite = new Favorites(customerId, productId, Instant.now());
        repository.save(myFavorite);
        

        List<Favorites> myFavoriteList = repository.findByCustomerId(customerId);

        assertEquals(1, myFavoriteList.size());
        assertEquals(customerId, myFavoriteList.get(0).getCustomerId());
        assertEquals(productId, myFavoriteList.get(0).getProductId());

    }
    
    @Test 
    public void test_findByCustomerIdAndProductId() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Favorites myFavorite = new Favorites(customerId, productId, Instant.now());
        repository.save(myFavorite);

        Optional<Favorites> favOpt = repository.findByCustomerIdAndProductId(customerId, productId);
        assertEquals(customerId, favOpt.get().getCustomerId());
        assertEquals(productId, favOpt.get().getProductId());
        
    }
    
    
    
    public void test_uniqueConstraint() {

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Favorites myFavorite = new Favorites(customerId, productId, Instant.now());
        repository.saveAndFlush(myFavorite);

        assertThrows(DuplicateFavoriteException.class, () -> repository.saveAndFlush(myFavorite)); 
    }

}
