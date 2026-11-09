package com.customer.payment.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import com.customer.payment.filter.GatewayAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class PaymentConfig {

    /**
     * This bean will provide the configuration as below
     * 1. disable csrf
     * 2. stateless sessions
     * 3. allow actuator endpoints -> /health and /info to bypass security, everything else needs authentication
     * 4. inject security context obtained from authorizer service for method security
     * @param security
     * @return
     * @throws Exception
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity security) throws Exception {

        return security
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new GatewayAuthenticationFilter(), AnonymousAuthenticationFilter.class)
            .build();
    }
}
