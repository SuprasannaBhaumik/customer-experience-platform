package com.customer.order.service;

import java.util.UUID;

import com.customer.order.dto.ProductResponse;
import com.customer.order.dto.RequestIdentity;

public interface ProductClient {

    ProductResponse getProduct(UUID productId, RequestIdentity identity);

}
