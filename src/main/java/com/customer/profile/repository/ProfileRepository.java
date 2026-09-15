package com.customer.profile.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.customer.profile.entity.CustomerProfile;

@Repository("myChoosenRepo")
public interface ProfileRepository  extends JpaRepository<CustomerProfile, UUID> {

    Optional<CustomerProfile> findByEmail(String email);
    
    Optional<CustomerProfile> findByFirstName(String firstName);

    Optional<CustomerProfile> findByLastName(String lastName);
    
    Optional<CustomerProfile> findByFirstNameAndLastName(String firstName, String lastName);

    Optional<CustomerProfile> findByEmailIgnoreCase(String email);

    @Query("select p from CustomerProfile p left join fetch p.preferences")
    List<CustomerProfile> findAllDetails_JPQL();

    @EntityGraph(attributePaths = {"preferences"})
    List<CustomerProfile> findAllBy();

}
