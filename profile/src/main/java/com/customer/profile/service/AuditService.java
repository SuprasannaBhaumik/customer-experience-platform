package com.customer.profile.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.customer.profile.dto.AuditRequest;
import com.customer.profile.dto.AuditResponse;
import com.customer.profile.entity.ProfileAudit;
import com.customer.profile.repository.ProfileAuditRepository;

@Service 
public class AuditService {

    @Autowired 
    ProfileAuditRepository profileAuditRepository;

    /**
     * Saves an audit in an independent transaction when called through Spring's proxy.
     * REQUIRES_NEW suspends any caller transaction. If this transaction commits
     * successfully, a later rollback in the caller does not remove the audit.
     */
    @Transactional (propagation = Propagation.REQUIRES_NEW)
    public AuditResponse makeAudit(UUID customerId, AuditRequest auditRequest) {
        // Use the method's customer ID (from the endpoint), not auditRequest.customerId().
        ProfileAudit profileAudit = 
            new ProfileAudit(
                customerId, 
                auditRequest.activityPerformed(), 
                auditRequest.createdTime()
            );
        // Spring commits this transaction after the method returns through its proxy;
        // save itself is not the commit, and an audit failure can still prevent persistence.
        profileAuditRepository.save(profileAudit);
        return new AuditResponse(
            profileAudit.getId(), 
            profileAudit.getCustomerId(), 
            profileAudit.getCreatedTime(),
            profileAudit.getActivity()
        );
    }
}
