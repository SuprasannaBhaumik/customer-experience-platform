package com.customer.favorites.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.favorites.domain.Favorites;
import com.customer.favorites.dto.FavoritesDTO;
import com.customer.favorites.exception.DuplicateFavoriteException;
import com.customer.favorites.exception.NoProductFoundForCustomerException;
import com.customer.favorites.repository.FavoritesRepository;

@Service
public class FavoritesService {

    @Autowired 
    private FavoritesRepository repository;

    @Transactional(readOnly = true)
    public List<FavoritesDTO> listAllFavoritesOfACustomer(UUID customerId) {
        return repository.findByCustomerId(customerId).stream()
                .map(favorite -> new FavoritesDTO(favorite.getFavoriteId(), favorite.getCustomerId(),
                        favorite.getProductId(), favorite.getCreatedAt()))
                .toList();

    }

    @Transactional 
    public FavoritesDTO saveFavorite(UUID customerId, UUID productId) {
        //same should not be saved twice
        if(repository.existsByCustomerIdAndProductId(customerId, productId)) {
            throw new DuplicateFavoriteException(customerId, productId);
        }
        Favorites favorite = repository.save(new Favorites(customerId, productId, Instant.now()));
        return new FavoritesDTO(favorite.getFavoriteId(), favorite.getCustomerId(),
                        favorite.getProductId(), favorite.getCreatedAt());
    }

    @Transactional
    public void deleteFavoriteOfCustomerByProduct(UUID customerId, UUID productId) {
        if (!repository.existsByCustomerIdAndProductId(customerId, productId)) {
            throw new NoProductFoundForCustomerException(customerId, productId);
        }
        repository.deleteByCustomerIdAndProductId(customerId, productId);
    }

}
