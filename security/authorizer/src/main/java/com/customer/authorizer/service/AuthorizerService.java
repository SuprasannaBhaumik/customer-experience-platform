package com.customer.authorizer.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import com.customer.authorizer.model.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service 
public class AuthorizerService {


    public AuthorizationDecision authorize( Jwt jwt, String method, String path) {

        boolean allowed;
        boolean pathStartCondition = path.startsWith("/api/products");
        Set<String> adminMethodSet = Set.of("POST", "PUT", "PATCH", "DELETE");
       
        Set<String> scopes = extractScopes(jwt);
        boolean canReadProducts = scopes.contains("product.read");
        boolean canWriteProducts = scopes.contains("product.write");
       
        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());

        if ("GET".equals(method) && pathStartCondition) {
            allowed = canReadProducts;
        } else if (adminMethodSet.contains(method) && pathStartCondition) {
            allowed = canWriteProducts || roles.contains("ADMIN");
        } else {
            allowed = false;
        }

        if (!allowed) {
            throw new AccessDeniedException(
                "Not authorized"
            );
        }
        
        return new AuthorizationDecision(true, jwt.getSubject(), roles, scopes);
    }

    private Set<String> extractScopes(Jwt jwt) {
        Object claim = jwt.getClaim("scope");

        // Some identity providers use "scp" instead of "scope".
        if (claim == null) {
            claim = jwt.getClaim("scp");
        }

        // Example: "product.read product.write"
        if (claim instanceof String scopeString) {
            return Arrays.stream(scopeString.trim().split("\\s+"))
                    .filter(scope -> !scope.isBlank())
                    .collect(Collectors.toSet());
        }

        // Example: ["product.read", "product.write"]
        if (claim instanceof Collection<?> scopeList) {
            return scopeList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(scope -> !scope.isBlank())
                    .collect(Collectors.toSet());
        }

        // Missing or unsupported claim: no permissions.
        return Set.of();
    }
}
