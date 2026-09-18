package com.customer.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.customer.profile.entity.CustomerPreferences;

public interface PreferenceRepository extends JpaRepository<CustomerPreferences, Long>{

}
