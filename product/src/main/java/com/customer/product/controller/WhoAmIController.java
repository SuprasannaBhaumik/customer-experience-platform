package com.customer.product.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

}
