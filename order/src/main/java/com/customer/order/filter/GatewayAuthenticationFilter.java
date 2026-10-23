package com.customer.order.filter;

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

public class GatewayAuthenticationFilter extends OncePerRequestFilter{

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String user = request.getHeader("X-Authenticated-User");
        String roles = request.getHeader("X-Authenticated-Roles");
        String scopes = request.getHeader("X-Authenticated-Scopes");

        if( user != null && !user.isBlank()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (roles != null)
                addAuthorities(roles, "ROLE_", authorities);
            if (scopes != null)
                addAuthorities(scopes, "SCOPE_", authorities);

            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }


    private void addAuthorities(String authorityType, String authorityPrefix, List<GrantedAuthority> authorities) {
        Arrays
            .stream(authorityType.split(","))
            .map(String::trim)
            .filter(type -> !type.isEmpty())
            .map(x -> new SimpleGrantedAuthority(authorityPrefix + x))
            .forEach(authorities::add);
    }

}
