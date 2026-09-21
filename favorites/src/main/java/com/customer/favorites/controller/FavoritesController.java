package com.customer.favorites.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.favorites.dto.FavoritesDTO;
import com.customer.favorites.service.FavoritesService;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/favorites")
public class FavoritesController {

    @Autowired 
    private FavoritesService service;

    @PostMapping("/{productId}")
    public ResponseEntity<FavoritesDTO> saveFavoriteProduct(@PathVariable UUID customerId, @PathVariable UUID productId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saveFavorite(customerId, productId));
    }

    @GetMapping
    public ResponseEntity<List<FavoritesDTO>> getFavoritesProductList(@PathVariable UUID customerId) {
        return ResponseEntity.status(HttpStatus.OK).body(service.listAllFavoritesOfACustomer(customerId));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteFavoriteProduct(@PathVariable UUID customerId, @PathVariable UUID productId) {
        service.deleteFavoriteOfCustomerByProduct(customerId, productId);
        return ResponseEntity.noContent().build();
    }


}
