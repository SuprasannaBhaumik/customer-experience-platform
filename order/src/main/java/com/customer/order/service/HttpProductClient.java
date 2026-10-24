package com.customer.order.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.customer.order.dto.ProductResponse;
import com.customer.order.dto.RequestIdentity;
import com.customer.order.exception.ProductNotAvailableForOrderException;

@Component 
public class HttpProductClient implements ProductClient {
     private final RestClient restClient;
    
    public HttpProductClient(RestClient productRestClient) {
        this.restClient = productRestClient;
    }

    @Override
    public ProductResponse getProduct(UUID productId, RequestIdentity identity) {

        return 
            restClient
                .get()
                .uri("/api/v1/products/{productId}", productId)
                .header("X-Authenticated-User", identity.subject())
                .header("X-Authenticated-Scopes", String.join(",", identity.scopes()))
                .retrieve()
                .onStatus( 
                    status -> status.value() == 404,
                    (request, response) -> {
                        throw new ProductNotAvailableForOrderException(productId);
                    }
                )
                .body(ProductResponse.class);
    }
}

