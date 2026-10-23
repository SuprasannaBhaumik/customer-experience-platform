package com.customer.order;

import java.math.BigDecimal;
import java.time.Instant;
import com.customer.order.domain.Order;
import com.customer.order.domain.OrderItem;
import com.customer.order.domain.OrderItemLink;
import com.customer.order.domain.OrderStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import com.customer.order.dto.*;
import com.customer.order.exception.OrderNotFoundException;
import com.customer.order.repository.*;
import com.customer.order.service.OrderService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OrderCrudTests {
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderItemLinkRepository links;

    private final UUID customerId = UUID.randomUUID();

    private OrderResponse seedOrder(int quantity) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PENDING_ALLOCATION);
        order.setShippingAddress("123 Main Street");
        order.setTotalAmount(new BigDecimal("12.50").multiply(BigDecimal.valueOf(quantity)));
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(order.getCreatedAt());
        orders.save(order);

        OrderItem item = new OrderItem();
        item.setProductId(UUID.randomUUID());
        item.setProductName("Book");
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("12.50"));
        items.save(item);

        OrderItemLink link = new OrderItemLink();
        link.setOrderId(order.getOrderId());
        link.setOrderItemId(item.getOrderItemId());
        links.save(link);
        entityManager.flush();
        entityManager.clear();
        return service.getOrder(customerId, new OrderRequest(order.getOrderId()));
    }

    @Test
    void loadsItemsThroughJoinTable() {
        var created = seedOrder(2);
        assertEquals(new BigDecimal("25.00"), created.totalAmount());
        entityManager.flush();
        entityManager.clear();
        UUID oldItemId = created.orderItems().getFirst().getOrderItemId();
        assertEquals(1, links.findByOrderId(created.orderId()).size());
        assertEquals(oldItemId, service.getOrder(customerId, new OrderRequest(created.orderId())).orderItems().getFirst().getOrderItemId());
        assertTrue(service.getOrders(customerId).stream().anyMatch(o -> o.orderId().equals(created.orderId())));


    }

    @Test
    void missingOrdersReturnNotFound() {
        UUID id = UUID.randomUUID();
        assertThrows(OrderNotFoundException.class, () -> service.getOrder(customerId, new OrderRequest(id)));
    }

    @Test
    void customersCannotReadOtherCustomersOrders() {
        var created = seedOrder(1);
        UUID otherCustomer = UUID.randomUUID();
        assertTrue(service.getOrders(otherCustomer).isEmpty());
        assertThrows(OrderNotFoundException.class,
            () -> service.getOrder(otherCustomer, new OrderRequest(created.orderId())));
    }
}
