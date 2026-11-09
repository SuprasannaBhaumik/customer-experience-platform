package com.customer.payment.integration.payment;

import com.customer.payment.integration.payment.dto.PaymentProviderRequest;
import com.customer.payment.integration.payment.dto.PaymentProviderResult;

public interface PaymentProviderClient {

    PaymentProviderResult charge (PaymentProviderRequest request);
    
}
