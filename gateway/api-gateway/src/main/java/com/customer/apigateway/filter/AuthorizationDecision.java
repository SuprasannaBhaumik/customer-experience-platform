package com.customer.apigateway.filter;

import java.util.List;
import java.util.Set;

public record AuthorizationDecision(
        boolean allowed,
        String subject,
        List<String> roles,
        Set<String> scopes) {
}
