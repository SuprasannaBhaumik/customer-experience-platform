package com.customer.order.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.order.dto.OrderRequest;
import com.customer.order.dto.OrderResponse;
import com.customer.order.dto.RequestIdentity;
import com.customer.order.service.OrderService;
import com.customer.order.util.RequestIdentityFactory;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    // should not be an api method to create orders.
    // rather the cart and checkout should contruct a payload and post an async event
    // this event when consumed should populate the tables accordingly.

    @GetMapping
    public List<OrderResponse> getOrders(
        @RequestHeader("X-Authenticated-User") String customerId,
        @RequestHeader(value = "X-Authenticated-Scopes", required = false) String scopes
    ) {
        RequestIdentity requestIdentity = RequestIdentityFactory.from(customerId, scopes);
        return orderService.getOrders(UUID.fromString(customerId), requestIdentity);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
        @RequestHeader("X-Authenticated-User") String customerId, 
        @RequestHeader(value = "X-Authenticated-Scopes", required = false) String scopes,
        @PathVariable UUID orderId) {

            RequestIdentity requestIdentity = RequestIdentityFactory.from(customerId, scopes);
            return orderService.getOrder(UUID.fromString(customerId), new OrderRequest(orderId), requestIdentity);
    }

}
