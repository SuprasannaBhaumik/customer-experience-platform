package com.customer.order.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.services.product")
public record ProductServiceProperties(
    URI baseUrl,
    Duration connectTimeout,
    Duration readTimeout
) {

    // fail at startup rather than calling the product service without timeouts
    public ProductServiceProperties {
        Objects.requireNonNull(connectTimeout, "app.services.product.connect-timeout must be set");
        Objects.requireNonNull(readTimeout, "app.services.product.read-timeout must be set");
    }

}
