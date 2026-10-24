package com.customer.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import com.customer.order.domain.Order;

@Repository
public interface OrderRepository extends CrudRepository<Order, UUID> {

    List<Order> findByCustomerId(UUID customerId);
    
    Optional<Order> findByOrderIdAndCustomerId(UUID orderId, UUID customerId);
}
