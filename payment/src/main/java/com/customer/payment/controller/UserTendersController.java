package com.customer.payment.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.customer.payment.dto.TenderResponse;
import com.customer.payment.service.TenderService;

@RestController
@RequestMapping("/api/v1/tenders")
public class UserTendersController {

    @Autowired
    private TenderService tenderService;

    @GetMapping
    public List<TenderResponse> getTenders(@RequestHeader("X-Authenticated-User") String customerId) {
        return tenderService.getTenders(UUID.fromString(customerId));
    }

    @GetMapping("/{tenderId}")
    public TenderResponse getTender(
        @RequestHeader("X-Authenticated-User") String customerId,
        @PathVariable UUID tenderId) {

            return tenderService.getTender(UUID.fromString(customerId), tenderId);
    }

}
