package com.customer.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.customer.profile.entity.ProfileAudit;

public interface ProfileAuditRepository extends JpaRepository<ProfileAudit, Long>{

}
