package com.customer.order.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.order.domain.Order;
import com.customer.order.domain.OrderItem;
import com.customer.order.domain.OrderItemLink;
import com.customer.order.dto.OrderRequest;
import com.customer.order.dto.OrderResponse;
import com.customer.order.exception.OrderNotFoundException;
import com.customer.order.repository.OrderItemLinkRepository;
import com.customer.order.repository.OrderItemRepository;
import com.customer.order.repository.OrderRepository;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderItemRepository orderItemRepository;
    
    @Autowired
    private OrderItemLinkRepository orderItemLinkRepository;


    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(UUID customerId) {
        List<OrderResponse> orders = new ArrayList<>();
        orderRepository.findByCustomerId(customerId).forEach(order -> {
            loadItems(order);
            orders.add(response(order));
        });
        return orders;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID customerId, OrderRequest request) {
        Order order = findOrder(customerId, request.orderId());
        loadItems(order);
        return response(order);
    }

    private Order findOrder(UUID customerId, UUID id) {
        return orderRepository.findByOrderIdAndCustomerId(id, customerId).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private void loadItems(Order order) {
        List<UUID> ids = orderItemLinkRepository.findByOrderId(order.getOrderId()).stream()
                .map(OrderItemLink::getOrderItemId).toList();
        List<OrderItem> items = new ArrayList<>();
        orderItemRepository.findAllById(ids).forEach(items::add);
        order.setOrderItems(items);
    }


    private OrderResponse response(Order order) {
        return new OrderResponse(order.getOrderId(), order.getCustomerId(), order.getStatus(),
                order.getShippingAddress(), order.getTotalAmount(), order.getCreatedAt(),
                order.getUpdatedAt(), List.copyOf(order.getOrderItems()));
    }
}
