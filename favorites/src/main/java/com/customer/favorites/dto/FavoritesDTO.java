package com.customer.favorites.dto;

import java.time.Instant;
import java.util.UUID;

public record FavoritesDTO(
    Long favoriteId,
    UUID customerId, 
    UUID productId,
    Instant createdAt
) {
}
