package com.customer.product.config;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.oauth2.core.authorization.OAuth2AuthorizationManagers.hasScope;

@Configuration 
@EnableMethodSecurity 
public class SecurityConfiguration {

    @Bean 
    SecurityFilterChain securityFilterChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint) throws Exception {

        http
            
            .csrf( csrf -> csrf.disable())
            

            .sessionManagement( session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Keep path rules broad here, as in the active configuration below.
            // Rules illustrate fine-grained HTTP method and role/scope checks
            // that should be avoided here. Add those authorization checks and any additional
            // security context checks at the method level using method security instead.
            // authority checks using hasAuthority() always uses prefixed SCOPE_ and ROLE_ for scopes and roles respectively.
            .authorizeHttpRequests(
                auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/token-info").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products/**").hasAuthority("SCOPE_product.read")//.hasAnyRole("USER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasAuthority("SCOPE_product.write")//hasAnyRole( "ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").access(hasScope("product.write"))//hasAnyRole( "ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/products/**").hasAuthority("SCOPE_product.write")//hasAnyRole( "ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasAuthority("SCOPE_product.write")//hasAnyRole( "ADMIN")
                    .anyRequest()
                    .authenticated()
            )

            //use the below for method level security checks
            /* .authorizeHttpRequests( 
                auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/v1/**").authenticated()
                    .requestMatchers("/who").authenticated()
            ) */
            
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(entryPoint)) // invalid/expired token        

            //.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint)) //wrong password

            .exceptionHandling( ex -> ex.authenticationEntryPoint(entryPoint)); // no creds

        return http.build();
    }

    // maps the "scope" claim to SCOPE_* (default behaviour) and the custom "roles" claim
    // issued by the authorization server to ROLE_*, so hasRole() checks work with JWTs
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Stream<GrantedAuthority> roleAuthorities =
                roles == null
                    ? Stream.empty()
                    : roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role));

            Collection<GrantedAuthority> authorities =
                Stream.concat(scopesConverter.convert(jwt).stream(), roleAuthorities).toList();
            return authorities;
        });
        return converter;
    }


    //for demonstration purposes only
    /* @Bean 
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
    } */
}
