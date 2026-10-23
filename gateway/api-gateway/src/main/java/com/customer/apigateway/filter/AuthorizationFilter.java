package com.customer.apigateway.filter;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component 
public class AuthorizationFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public AuthorizationFilter(@Value("${authorizer.base-url:http://localhost:9001}") String authorizerBaseUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(authorizerBaseUrl)
            .build();
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
       
        String authorization = exchange
            .getRequest()
            .getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return webClient
            .post()
            .uri("/internal/authorize")
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .header("X-Original-Method", exchange.getRequest().getMethod().name())
            .header("X-Original-Path", exchange.getRequest().getURI().getPath())
            .retrieve()
            .bodyToMono(AuthorizationDecision.class)
            .flatMap(decision -> {
                // the authorizer only answers 200 for an allowed request; denials arrive as 403 in onErrorResume
                ServerHttpRequest request = exchange
                    .getRequest()
                    .mutate()
                    .headers( headers -> {
                        headers.set("X-Authenticated-User", decision.subject());
                        headers.set("X-Authenticated-Roles", String.join(",", decision.roles()));
                        headers.set("X-Authenticated-Scopes", String.join(",", decision.scopes()));
                    })
                    .build();
                return chain.filter(exchange.mutate().request(request).build());
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                HttpStatusCode status = switch (ex.getStatusCode().value()) {
                    case 400 -> HttpStatus.BAD_REQUEST;
                    case 401 -> HttpStatus.UNAUTHORIZED;
                    case 403 -> HttpStatus.FORBIDDEN;
                    case 404 -> HttpStatus.NOT_FOUND;
                    case 429 -> HttpStatus.TOO_MANY_REQUESTS;
                    case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
                    case 502 -> HttpStatus.BAD_GATEWAY;
                    case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
                    case 504 -> HttpStatus.GATEWAY_TIMEOUT;
                    default -> ex.getStatusCode();
                };
                exchange.getResponse().setStatusCode(status);
                return exchange.getResponse().setComplete();
            });
    }

    


}
