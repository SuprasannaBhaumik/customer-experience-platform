package com.customer.favorites.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.customer.favorites.domain.Favorites;

@Repository 
public interface FavoritesRepository extends JpaRepository<Favorites, Long> {

    List<Favorites> findByCustomerId(UUID customerId);
    
    Optional<Favorites> findByCustomerIdAndProductId(UUID customerId, UUID productId);
    
    boolean existsByCustomerIdAndProductId(UUID customerId, UUID productId);

    void deleteByCustomerIdAndProductId(UUID customerId, UUID productId);

}
