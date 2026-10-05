package com.customer.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.customer.product.domain.Product;
import com.customer.product.dto.InventoryStatus;
import com.customer.product.repository.ProductRepository;

// Full context so the real ProductService (and its @PreAuthorize rules) is used.
// The repository is mocked, so the datasource/JPA auto-configurations are excluded
// and caching is switched off to keep the test free of Postgres and Redis.
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    "spring.cache.type=none"
})
@AutoConfigureMockMvc
public class ProductControllerSecurityTest {

    private static final String SKU = "SKU-1";
    private static final String NAME = "Keyboard";
    private static final String DESCRIPTION = "Mechanical keyboard";
    private static final String PRICE = "1000";
    private static final String INVENTORY_STATUS = "IN_STOCK";
    private static final String MY_PRODUCT = """
        {
            "sku": "%s",
            "name": "%s",
            "price": %s,
            "inventoryStatus": "%s"
        }
        """.formatted(SKU, NAME, PRICE, INVENTORY_STATUS);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductRepository productRepository;

    @Test
    public void nonAuthenticatedUserShouldReceive401() throws Exception{

        mockMvc.perform(
            get("/api/v1/products/{id}", UUID.randomUUID())
        ).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void userCanReadProduct() throws Exception {

        UUID id = UUID.randomUUID();

        Product product = new Product(SKU, NAME, DESCRIPTION, new BigDecimal(PRICE), InventoryStatus.valueOf(INVENTORY_STATUS));
        product.setProductId(id);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        mockMvc
            .perform(get("/api/v1/products/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(id.toString()))
            .andExpect(jsonPath("$.sku").value(SKU))
            .andExpect(jsonPath("$.name").value(NAME));

    }

    @Test
    @WithMockUser ( username = "user", roles = "USER")
    public void cannotCreateProductWithUserRole() throws Exception {

        mockMvc
            .perform(
                post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MY_PRODUCT)
            )
            .andExpect(status().isForbidden());

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @WithMockUser ( username = "admin", roles = "ADMIN")
    public void canCreateProductWithAdminRole() throws Exception {

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc
            .perform(
                post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MY_PRODUCT)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sku").value(SKU));
    }


}
