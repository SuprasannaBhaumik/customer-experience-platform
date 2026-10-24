package com.customer.order.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.order.domain.Order;
import com.customer.order.domain.OrderItem;
import com.customer.order.domain.OrderItemLink;
import com.customer.order.dto.OrderRequest;
import com.customer.order.dto.OrderResponse;
import com.customer.order.dto.RequestIdentity;
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

    /** We do not need product client for getting user orders, since that is more important while order creation when
     * inventory will be searched and the quantity will be offset by what we order.
     * It is configured here now so that we can show the product image if it exists in the inventory or show a dummy image
     */
    @Autowired 
    private HttpProductClient productClient;

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(UUID customerId, RequestIdentity requestIdentity) {
        List<OrderResponse> orders = new ArrayList<>();
        orderRepository.findByCustomerId(customerId).forEach(order -> {
            loadItems(order);
            orders.add(response(order));
        });
        //inorder to get products - we need to pass to productRestClient with requestIdentity
        return orders;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID customerId, OrderRequest request, RequestIdentity requestIdentity) {
        Order order = findOrder(customerId, request.orderId());
        loadItems(order);
        //inorder to get products - we need to pass to productRestClient with requestIdentity
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
        //inorder to get products - we need to pass to productRestClient with requestIdentity
        order.setOrderItems(items);
    }


    private OrderResponse response(Order order) {
        return new OrderResponse(order.getOrderId(), order.getCustomerId(), order.getStatus(),
                order.getShippingAddress(), order.getTotalAmount(), order.getCreatedAt(),
                order.getUpdatedAt(), List.copyOf(order.getOrderItems()));
    }
}
