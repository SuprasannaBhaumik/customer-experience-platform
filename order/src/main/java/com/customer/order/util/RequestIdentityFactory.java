package com.customer.order.util;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.customer.order.dto.RequestIdentity;

public final class RequestIdentityFactory {

    private RequestIdentityFactory() {
    }

    public static RequestIdentity from(String subject, String scopes) {
        return new RequestIdentity(subject, parseScopes(scopes), Set.of());
    }

    private static Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
