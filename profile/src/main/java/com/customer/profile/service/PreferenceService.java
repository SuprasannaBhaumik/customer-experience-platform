package com.customer.profile.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.customer.profile.dto.CustomerPreferenceResponse;
import com.customer.profile.dto.PreferenceRequest;
import com.customer.profile.entity.CustomerPreferences;
import com.customer.profile.entity.CustomerProfile;
import com.customer.profile.repository.PreferenceRepository;

@Service 
public class PreferenceService {

    @Autowired 
    private PreferenceRepository preferenceRepository;

    /**
     * Adds new preferences without replacing existing ones.
     * REQUIRED joins the caller's transaction, or starts one if none exists, when
     * invoked through Spring's proxy. A caller's rollback also rolls back these saves.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<CustomerPreferenceResponse> savePreferences(CustomerProfile customer, List<PreferenceRequest> customerPreferenceRequestList) {
        List<CustomerPreferences> preferencesList = new ArrayList<>();
        
        for(PreferenceRequest customerPreference: customerPreferenceRequestList) { 
            CustomerPreferences preference = new CustomerPreferences(customerPreference.size());
            // Set the owning side of the relationship to associate the row with the customer.
            preference.setCustomer(customer);
            preferencesList.add(preference);
        }
        
        // Saving does not independently commit when participating in the caller's transaction.
        preferenceRepository.saveAll(preferencesList);
        
        // DTOs describe the saved entities; returning them does not guarantee a later commit.
        return preferencesList.stream().map( preference -> new CustomerPreferenceResponse(preference.getPreferenceId(), preference.getCustomerSize())).toList();
    }
}
