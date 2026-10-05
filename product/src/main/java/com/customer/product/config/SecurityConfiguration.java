package com.customer.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration 
@EnableMethodSecurity 
public class SecurityConfiguration {

    @Bean 
    SecurityFilterChain securityFilterChain(HttpSecurity security, RestAuthenticationEntryPoint entryPoint) throws Exception {

        security
            .csrf( csrf -> csrf.disable())
            
            // Keep path rules broad here, as in the active configuration below.
            // The commented-out rules illustrate fine-grained HTTP method and role checks
            // that should be avoided here. Add those authorization checks and any additional
            // security context checks at the method level using method security instead.
            /*

            .authorizeHttpRequests(
                auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/products/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/products/**").hasAnyRole( "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/products/**").hasAnyRole( "ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/products/**").hasAnyRole( "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/products/**").hasAnyRole( "ADMIN")
                    .anyRequest()
                    .authenticated()
            ) */
           .authorizeHttpRequests( 
                auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/v1/**").authenticated()
                    .requestMatchers("/who").authenticated()
            )
            .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint)) //wrong password
            .exceptionHandling( ex -> ex.authenticationEntryPoint(entryPoint)); // no creds

        return security.build();
    }


    //for demonstration purposes only
    @Bean 
    UserDetailsService userDetailsService(PasswordEncoder encoder) {

        UserDetails user = 
            User.withUsername("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build();
        

        UserDetails admin = 
            User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build();
        
        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean 
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
