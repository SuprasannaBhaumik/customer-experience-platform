package com.customer.profile.enums;

// stored as strings in customer_profile_roles; the authorization server turns each into a ROLE_* authority
public enum Role {
    USER,
    ADMIN,
    SUPER_ADMIN,
    POWER_USER
}
