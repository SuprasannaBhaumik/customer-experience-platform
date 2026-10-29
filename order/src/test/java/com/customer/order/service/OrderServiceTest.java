package com.customer.order.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.customer.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock 
    ProductClient productClient;

    @Mock 
    OrderRepository orderRepository;

    @InjectMocks 
    OrderService orderService;


    

}
