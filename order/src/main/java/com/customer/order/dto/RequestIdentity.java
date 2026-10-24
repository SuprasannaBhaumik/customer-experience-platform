package com.customer.order.dto;

import java.util.Set;

public record RequestIdentity(
        String subject,
        Set<String> scopes,
        Set<String> roles
) {
}