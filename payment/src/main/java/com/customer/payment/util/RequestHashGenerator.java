package com.customer.payment.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.customer.payment.dto.CreatePaymentRequest;

@Component 
public class RequestHashGenerator {


    public String hash(CreatePaymentRequest request) {
        String canonical = 
            request.orderId()
                + "|"
            + request.customerId()
                + "|"
            + request.amount().stripTrailingZeros().toPlainString()
                + "|"
            + request.currency().toUpperCase(Locale.ROOT)
                + "|"
            + request.paymentMethodId();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }

    }
}
