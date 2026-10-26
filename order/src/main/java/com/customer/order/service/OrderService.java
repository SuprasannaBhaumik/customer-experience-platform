package com.customer.order.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.order.domain.Order;
import com.customer.order.domain.OrderItem;
import com.customer.order.domain.OrderItemLink;
import com.customer.order.dto.InventoryStatus;
import com.customer.order.dto.OrderItemResponse;
import com.customer.order.dto.OrderRequest;
import com.customer.order.dto.OrderResponse;
import com.customer.order.dto.RequestIdentity;
import com.customer.order.exception.OrderNotFoundException;
import com.customer.order.exception.ProductNotAvailableForOrderException;
import com.customer.order.exception.ProductServiceUnavailableException;
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

    /** Used on reads to show the live inventory status of each ordered product.
     * If the product service cannot answer, the order is still returned with a null status.
     */
    @Autowired
    private ProductClient productClient;

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(UUID customerId, RequestIdentity requestIdentity) {
        List<OrderResponse> orders = new ArrayList<>();
        // the same product can appear in many orders - look it up once per request
        Map<UUID, InventoryStatus> statuses = new HashMap<>();
        orderRepository.findByCustomerId(customerId).forEach(order -> {
            loadItems(order);
            orders.add(response(order, requestIdentity, statuses));
        });
        return orders;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID customerId, OrderRequest request, RequestIdentity requestIdentity) {
        Order order = findOrder(customerId, request.orderId());
        loadItems(order);
        return response(order, requestIdentity, new HashMap<>());
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


    private OrderResponse response(Order order, RequestIdentity requestIdentity, Map<UUID, InventoryStatus> statuses) {
        List<OrderItemResponse> items = 
            order
                .getOrderItems()
                .stream()
                .map(item -> itemResponse(item, statuses.computeIfAbsent(item.getProductId(), id -> inventoryStatusOf(id, requestIdentity))))
                .toList();
        return new OrderResponse(order.getOrderId(), order.getCustomerId(), order.getStatus(),
                order.getShippingAddress(), order.getTotalAmount(), order.getCreatedAt(),
                order.getUpdatedAt(), items);
    }

    private OrderItemResponse itemResponse(OrderItem item, InventoryStatus inventoryStatus) {
        return new OrderItemResponse(
            item.getOrderItemId(),
            item.getProductId(),
            item.getProductName(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getLineTotal(),
            inventoryStatus
        );
    }

    private InventoryStatus inventoryStatusOf(UUID productId, RequestIdentity identity) {
        try {
            return productClient.getProduct(productId, identity).inventoryStatus();
        } catch (ProductNotAvailableForOrderException | ProductServiceUnavailableException e) {
            return null;
        }
    }
}
