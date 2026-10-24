package com.customer.order.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.customer.order.dto.ProductResponse;
import com.customer.order.dto.RequestIdentity;
import com.customer.order.exception.ProductNotAvailableForOrderException;
import com.customer.order.exception.ProductServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class HttpProductClient implements ProductClient {

    private final RestClient restClient;

    public HttpProductClient(RestClient productRestClient) {
        this.restClient = productRestClient;
    }

    @Override
    @CircuitBreaker(name = "product", fallbackMethod = "productUnavailable")
    public ProductResponse getProduct(UUID productId, RequestIdentity identity) {
        try {
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
                    .onStatus(
                        status -> status.is5xxServerError(),
                        (request, response) -> {
                            throw new ProductServiceUnavailableException();
                        }
                    )
                    .body(ProductResponse.class);
        } catch (ResourceAccessException e) {
            // connect timeout, read timeout, connection refused
            throw new ProductServiceUnavailableException(e);
        }
    }

    // only called while the circuit is open; any other exception propagates as thrown
    private ProductResponse productUnavailable(
        UUID productId, 
        RequestIdentity identity, 
        CallNotPermittedException e
    ) {
        throw new ProductServiceUnavailableException(e);
    }
}
