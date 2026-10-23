package com.customer.authorizer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration 
@EnableWebSecurity 
public class SecurityConfiguration {

    /** my understanding of this configuration class is as follows:
     * authorizer is supposed to intercept all requests and validate the tokens before they can be fowarded to controller
     * there is a bean that can help to decode the jwt and do so for every reqeust, this should be injected into resourceserver config
     * I am not sure why is the controller even needed, if I am able to do the jwt validation with a bean >> 
     * Controller is going to give a decision -> AuthorizeDecision if the request is allowed or not.
     */

    @Bean 
    SecurityFilterChain filterChain( HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests( httpRequests -> httpRequests.anyRequest().authenticated())
            //we need to inject our jwtAuthenticationConverter here in the below
            .oauth2ResourceServer(
                resourceServer -> resourceServer.jwt( 
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            )
            .build();
    }

    
}
