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
