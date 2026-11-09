package com.customer.payment.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.customer.payment.dto.TenderResponse;

@Service 
public class TenderService {

    public List<TenderResponse> getTenders(UUID fromString) {

        return null;
    }

    public TenderResponse getTender(UUID fromString, UUID tenderId) {
        return null;
    }

}
