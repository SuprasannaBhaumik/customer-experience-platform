package com.customer.order.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.order.dto.OrderRequest;
import com.customer.order.dto.OrderResponse;
import com.customer.order.service.OrderService;

@RestController
@RequestMapping("/api/v1/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    //should not be an api method to create orders. 
    //rather the cart and checkout should contruct a payload and post an event
    // this event when consumed should populate the tables accordingly.

    @GetMapping
    public List<OrderResponse> getOrders(@RequestHeader("X-Authenticated-User") UUID customerId) {
        return orderService.getOrders(customerId);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@RequestHeader("X-Authenticated-User") UUID customerId, @PathVariable UUID orderId) {
        return orderService.getOrder(customerId, new OrderRequest(orderId));
    }

}
