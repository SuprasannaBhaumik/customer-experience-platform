package com.customer.authorizationserver.service;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Spring's User plus the profile's customer id, so the token customizer
 * can put that id in the sub claim instead of the username.
 */
public class CustomerUserDetails extends User {

    private final UUID customerId;

    public CustomerUserDetails(UUID customerId, String username, String password,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.customerId = customerId;
    }

    public UUID getCustomerId() {
        return customerId;
    }
}
