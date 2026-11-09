package com.customer.payment.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.customer.payment.domain.Tender;

@Repository 
public interface TenderRepository extends JpaRepository<Tender, UUID>{

}
