package com.customer.authorizer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration 
public class JwtAuthorityConfig {

    @Bean 
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        //converter 1: extract oauth scopes
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("SCOPE_");

        //converter 2: extract custom application roles like SUPER_ADMIN, POWER_USER, etc
        JwtGrantedAuthoritiesConverter roleConverter = new JwtGrantedAuthoritiesConverter();
        roleConverter.setAuthoritiesClaimName("roles");
        roleConverter.setAuthorityPrefix("ROLE_");

        //combine roles and scopes
        DelegatingJwtGrantedAuthoritiesConverter combined = 
            new DelegatingJwtGrantedAuthoritiesConverter(scopeConverter, roleConverter);

        JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(combined);

        return converter;
    }

}
