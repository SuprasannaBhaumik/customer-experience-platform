package com.customer.product.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.customer.product.dto.InventoryStatus;
import com.customer.product.dto.ProductRequest;
import com.customer.product.repository.ProductRepository;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    "spring.cache.type=none"
})
public class ProductServiceSecurityTest {


    @Autowired 
    private ProductService productService;

    @MockitoBean
    private ProductRepository productRepository;

    private static final String SKU = "SKU-1";
    private static final String NAME = "Keyboard";
    private static final String DESCRIPTION = "Mechanical keyboard";
    private static final BigDecimal PRICE = new BigDecimal("1000");
    private static final InventoryStatus INVENTORY_STATUS = InventoryStatus.IN_STOCK;

    @Test
    @WithMockUser(username="user", roles = "USER")
    public void mustNotCreateProduct() throws Exception {

        ProductRequest myProduct = new ProductRequest(SKU, NAME, DESCRIPTION, PRICE, INVENTORY_STATUS);
        assertThrows(
            AccessDeniedException.class, 
            () -> productService.createProduct(myProduct)
        );

    }

}
