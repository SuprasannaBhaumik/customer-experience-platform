package com.customer.product.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Builds the Spring Security Authentication from the identity headers the API gateway adds
 * after the authorizer has validated the JWT and allowed the request.
 *
 * X-Authenticated-User   -> principal name (the token's subject)
 * X-Authenticated-Roles  -> ROLE_* authorities, used by hasRole(...) in @PreAuthorize
 * X-Authenticated-Scopes -> SCOPE_* authorities, used by hasAuthority('SCOPE_...') in @PreAuthorize
 *
 * The headers are trusted as-is, so this service must only be reachable through the gateway.
 * Not a @Component on purpose: Spring Boot would also register it as a plain servlet filter
 * and it would run twice. It is added to the security filter chain in SecurityConfiguration.
 */
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String user = request.getHeader("X-Authenticated-User");
        String roles = request.getHeader("X-Authenticated-Roles");
        String scopes = request.getHeader("X-Authenticated-Scopes");

        // no user header -> leave the request unauthenticated, the filter chain answers 401
        if (user != null && !user.isBlank()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (roles != null)
                addAuthorities(roles, "ROLE_", authorities);
            if (scopes != null)
                addAuthorities(scopes, "SCOPE_", authorities);

            var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }

    // "ADMIN,USER" -> ROLE_ADMIN, ROLE_USER ; "customer.read,customer.write" -> SCOPE_customer.read, SCOPE_customer.write
    private void addAuthorities(String headerValue, String authorityPrefix, List<GrantedAuthority> authorities) {
        Arrays
            .stream(headerValue.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> new SimpleGrantedAuthority(authorityPrefix + value))
            .forEach(authorities::add);
    }

}
