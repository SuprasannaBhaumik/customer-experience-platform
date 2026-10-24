package com.customer.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration 
@EnableConfigurationProperties(ProductServiceProperties.class)
public class RestClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean 
    RestClient productRestClient(RestClient.Builder builder, ProductServiceProperties properties) {
        return 
            builder
                .baseUrl(properties.baseUrl().toString())
                .build();
    }
}
