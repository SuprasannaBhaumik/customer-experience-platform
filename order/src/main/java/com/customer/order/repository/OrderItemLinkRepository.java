package com.customer.order.repository;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import com.customer.order.domain.OrderItemLink;

@Repository
public interface OrderItemLinkRepository extends CrudRepository<OrderItemLink, UUID> {
    java.util.List<OrderItemLink> findByOrderId(UUID orderId);
    void deleteByOrderId(UUID orderId);
}
