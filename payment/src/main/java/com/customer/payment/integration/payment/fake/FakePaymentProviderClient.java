package com.customer.payment.integration.payment.fake;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.customer.payment.integration.payment.PaymentProviderClient;
import com.customer.payment.integration.payment.dto.PaymentProviderRequest;
import com.customer.payment.integration.payment.dto.PaymentProviderResult;
import com.customer.payment.integration.payment.dto.PaymentProviderStatus;

@Service 
public class FakePaymentProviderClient implements PaymentProviderClient{

    private final ConcurrentMap<String, PaymentProviderResult> results = new ConcurrentHashMap<>();

    @Override
    public PaymentProviderResult charge(PaymentProviderRequest request) {
        return results.computeIfAbsent(request.idempotencyKey(), key -> executeCharge(request));
    }

    private PaymentProviderResult executeCharge(PaymentProviderRequest request) {
        
        if("pm_fail".equalsIgnoreCase(request.paymentMethodId())) {
            return new PaymentProviderResult(
                PaymentProviderStatus.DECLINED,
                null,
                "DECLINED",
                "Payment declined"
            );
        }

        if("pm_unknown".equalsIgnoreCase(request.paymentMethodId())) {
            return new PaymentProviderResult(
                PaymentProviderStatus.UNKNOWN,
                null,
                "UNKNOWN",
                "Provider result uncertain"
            );
        }

        return new PaymentProviderResult(
            PaymentProviderStatus.SUCCEEDED,
            "provider_" + UUID.randomUUID(),
            null,
            null
        );
    }


}
