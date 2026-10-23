package com.customer.authorizationserver.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only view of the profile service's customer_profile table, holding only what login needs.
 * The profile service owns this schema; nothing here ever writes to it.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Immutable
@Table(name = "customer_profile")
public class CustomUser {

    @Id
    private UUID customerId;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    // plain strings (USER, ADMIN, ...) so this service does not depend on the profile service's Role enum
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "customer_profile_roles", joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();
}
