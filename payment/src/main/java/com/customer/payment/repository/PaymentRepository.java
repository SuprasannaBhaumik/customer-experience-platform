package com.customer.payment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.customer.payment.domain.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByCustomerId(UUID customerId);

    Optional<Payment> findByPaymentIdAndCustomerId(UUID paymentId, UUID customerId);
}
