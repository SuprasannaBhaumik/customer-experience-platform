package com.customer.product.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
public class WhoAmIController {

    @RequestMapping("/who")
    public ResponseEntity<Map<String, Object>> userDetails(Authentication authentication) {

        return 
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    Map.of(
                        "name", authentication.getName(), 
                        "authorities", authentication.getAuthorities()
                    )
                );
    }

    @RequestMapping("/token-info")
    public Map<String, Object> getTokenInfo(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "subject", jwt.getSubject(),
            "audience", jwt.getAudience(),
            "expiry", jwt.getExpiresAt(),
            "issuer", jwt.getIssuer()
        );
    }

}
