package com.customer.authorizer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import com.customer.authorizer.model.AuthorizationDecision;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.authorizer.service.AuthorizerService;

@RestController
@RequestMapping("/internal")
public class AuthorizerController {

    @Autowired 
    AuthorizerService authorizerService;

    @PostMapping("/authorize")
    public AuthorizationDecision authorize(
        @AuthenticationPrincipal Jwt jwt, 
        @RequestHeader("X-Original-Method") String method,
        @RequestHeader("X-Original-Path") String path
    ) {
        
        return authorizerService
            .authorize(
                jwt,
                method,
                path
            );
    }

}
