package com.customer.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import com.customer.product.filter.GatewayAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    /*
     * The JWT is validated once, by the authorizer (signature, iss, exp/nbf, aud) when the gateway
     * asks it for a decision. This service trusts the X-Authenticated-* headers the gateway adds
     * afterwards, so it is no longer an OAuth2 resource server.
     *
     * Path rules stay broad here (authenticated or not); the role/scope checks live on the
     * service methods as @PreAuthorize, which @EnableMethodSecurity switches on.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint) throws Exception {

        return http
            .csrf( csrf -> csrf.disable())
            .sessionManagement( session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(
                auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .anyRequest()
                    .authenticated()
            )
            // must run before AnonymousAuthenticationFilter, otherwise the request is already
            // marked anonymous by the time the gateway headers are read
            .addFilterBefore(new GatewayAuthenticationFilter(), AnonymousAuthenticationFilter.class)
            .exceptionHandling( ex -> ex.authenticationEntryPoint(entryPoint)) // no identity headers -> 401
            .build();
    }

}
