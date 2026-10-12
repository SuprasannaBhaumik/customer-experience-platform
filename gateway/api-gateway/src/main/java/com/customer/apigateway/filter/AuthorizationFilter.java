package com.customer.apigateway.filter;


import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component 
public class AuthorizationFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public AuthorizationFilter() {
        this.webClient = WebClient.builder()
            .baseUrl("http://localhost:9001")
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
                if(!decision.allowed()) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
                ServerHttpRequest request = exchange
                    .getRequest()
                    .mutate()
                    .headers( headers -> {
                        headers.set("X-Authenticated-User", decision.subject());
                        headers.set("X-Authenticated-Roles", String.join(",", decision.roles()));
                        headers.set("X-Authenticate-Scopes", String.join(",", decision.scopes()));
                    })
                    .build();
                return chain.filter(exchange.mutate().request(request).build());
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                // authorizer replies 401 for a bad token and 403 when access is denied
                HttpStatus status = ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.FORBIDDEN;
                exchange.getResponse().setStatusCode(status);
                return exchange.getResponse().setComplete();
            });
    }

    


}
