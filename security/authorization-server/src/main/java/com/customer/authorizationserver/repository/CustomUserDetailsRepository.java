package com.customer.authorizationserver.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.customer.authorizationserver.entity.CustomUser;

@Repository
public interface CustomUserDetailsRepository extends JpaRepository<CustomUser, UUID> {

    Optional<CustomUser> findByUsername(String username);
}
