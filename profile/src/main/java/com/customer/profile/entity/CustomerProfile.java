package com.customer.profile.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.customer.profile.enums.Role;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer_profile")
public class CustomerProfile { 

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID customerId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    // login name used by the authorization server
    @Column(unique = true, nullable = false)
    private String username;

    // BCrypt hash; nullable so profiles created before login existed simply cannot sign in
    @Column(name = "password_hash")
    private String passwordHash;

    // kept in its own table, one row per role; eager because every login needs them
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "customer_profile_roles", joinColumns = @JoinColumn(name = "customer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = new HashSet<>();

    @OneToMany(
        mappedBy = "customer",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<CustomerPreferences> preferences = new ArrayList<CustomerPreferences>();

    public void addPreference(CustomerPreferences p) {
        preferences.add(p);
        p.setCustomer(this);
    }

    public void deletePreference(CustomerPreferences p) {
        preferences.remove(p);
        p.setCustomer(null);
    }

    public CustomerProfile(String firstName, String lastName, String email,
            String username, String passwordHash, Set<Role> roles) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = new HashSet<>(roles);
    }
}
