package com.customer.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.customer.payment.domain.IdempotencyRecordEntity;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long>{

    Optional<IdempotencyRecordEntity> findBySubjectAndIdempotencyKey(String subject, String idempotencyKey);

}
