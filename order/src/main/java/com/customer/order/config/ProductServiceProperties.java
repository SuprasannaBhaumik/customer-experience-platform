package com.customer.order.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.services.product")
public record ProductServiceProperties(
    URI baseUrl,
    Duration connectTimeout,
    Duration readTimeout
) {

}
