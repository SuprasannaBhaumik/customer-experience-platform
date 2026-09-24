package com.customer.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.customer.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    List<Product> findByNameContainingIgnoreCase(String name);

    boolean existsBySkuIgnoreCase(String sku);

}
