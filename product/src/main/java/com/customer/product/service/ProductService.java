package com.customer.product.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.product.domain.Product;
import com.customer.product.dto.InventoryStatus;
import com.customer.product.dto.ProductDTO;
import com.customer.product.dto.ProductRequest;
import com.customer.product.exception.ProductNotFoundException;
import com.customer.product.repository.ProductRepository;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;


    public ProductDTO createProduct(ProductRequest productRequest) {
        Product newProduct = new Product(
            productRequest.sku(), 
            productRequest.name(), 
            productRequest.description(), 
            productRequest.price(), 
            productRequest.inventoryStatus()
        );
        newProduct.setCreatedAt(Instant.now());
        newProduct.setUpdatedAt(Instant.now());

        //more likely a service should determine the status for the below
        newProduct.setInventoryStatus(InventoryStatus.IN_STOCK);
        
        return toDTO(productRepository.save(newProduct));
    }

    @Transactional(readOnly = true)
    public Optional<Product> findBySkuIgnoreCase(String sku) {
        return productRepository.findBySkuIgnoreCase(sku);
    }

    @Transactional(readOnly = true)
    public List<Product> findByNameContainingIgnoreCase(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    @Transactional(readOnly = true)
    public boolean existsBySkuIgnoreCase(String sku) {
        return productRepository.existsBySkuIgnoreCase(sku);
    }

    @Transactional (readOnly = true)
    @Cacheable(
        cacheNames = "products",
        key = "#productId"
    )
    public ProductDTO getProduct(UUID productId) {

        Product product = productRepository.findById(productId)
        .orElseThrow( () -> new ProductNotFoundException(productId));

        return toDTO(product);
    }


    private ProductDTO toDTO(Product product) {
        return new ProductDTO(
                product.getProductId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getInventoryStatus());
    }


}
