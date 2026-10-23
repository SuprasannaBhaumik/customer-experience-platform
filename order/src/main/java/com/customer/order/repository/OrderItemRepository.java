package com.customer.order.repository;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import com.customer.order.domain.OrderItem;

@Repository
public interface OrderItemRepository extends CrudRepository<OrderItem, UUID> {
}
