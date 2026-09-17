package com.customer.profile.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.customer.profile.dto.CreateProfileRequest;
import com.customer.profile.dto.CustomerPreferenceRequest;
import com.customer.profile.dto.CustomerPreferenceResponse;
import com.customer.profile.dto.PatchProfileRequest;
import com.customer.profile.dto.PreferenceRequest;
import com.customer.profile.dto.ProfileResponse;
import com.customer.profile.dto.UpdateProfileAndPreferenceRequest;
import com.customer.profile.dto.UpdateProfileRequest;
import com.customer.profile.entity.CustomerPreferences;
import com.customer.profile.entity.CustomerProfile;
import com.customer.profile.entity.ProfileAudit;
import com.customer.profile.exception.DuplicateEmailException;
import com.customer.profile.exception.PreferenceNotFoundException;
import com.customer.profile.exception.ProfileNotFoundException;
import com.customer.profile.repository.ProfileAuditRepository;
import com.customer.profile.repository.ProfileRepository;

@Service
public class ProfileService {

    // The rule with multiple repository annotations is if provided with @qualifier
    // or
    // @primary the spring Ioc container will know which bean to inject at runtime
    @Autowired
    @Qualifier("myChoosenRepo")
    private ProfileRepository repository;

    @Autowired 
    private ProfileAuditRepository auditRepository;

    public ProfileResponse saveProfile(CreateProfileRequest request) {
        if (repository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new DuplicateEmailException(request.email());
        }
        CustomerProfile profile = repository
                .save(new CustomerProfile(request.firstName(), request.lastName(), request.email()));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), null);
    }

    public ProfileResponse getProfile(UUID customerId) {
        CustomerProfile profile = repository.findById(customerId)
                .orElseThrow(() -> new ProfileNotFoundException(customerId));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), null);
    }

    public ProfileResponse findProfileByEmail(String email) {
        CustomerProfile profile = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Profile with the email not found"));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), null);
    }

    public String deleteProfileByCustomerId(UUID customerId) {
        if (customerId != null) {
            repository
                .findById(customerId)
                .orElseThrow(() -> new ProfileNotFoundException(customerId));
            repository.deleteById(customerId);
            return "success";
        }
        return "failure";
    }

    public String updateCustomerProfile(UUID customerId, UpdateProfileRequest request) {
        if (customerId != null) {
            CustomerProfile customer = repository.findById(customerId)
                    .orElseThrow(() -> new ProfileNotFoundException(customerId));
            customer.setFirstName(request.firstName());
            customer.setFirstName(request.lastName());
            customer.setEmail(request.email());
            repository.save(customer);
            return "success";
        }
        return "failure";
    }

    public String patchCustomerProfile(UUID customerId, PatchProfileRequest request) {
        if (customerId != null) {
            CustomerProfile customer = repository.findById(customerId)
                    .orElseThrow(() -> new ProfileNotFoundException(customerId));
            if (request.firstName() != null)
                customer.setFirstName(request.firstName());
            if (request.lastName() != null)
                customer.setLastName(request.lastName());
            if (request.email() != null)
                customer.setEmail(request.email());
            repository.save(customer);
            return "success";
        }
        return "failure";
    }


    @Transactional
    public CustomerPreferenceResponse addPreference(CustomerPreferenceRequest request, UUID customerId) {
        CustomerProfile customer = repository.findById(customerId).orElseThrow(() -> new ProfileNotFoundException(customerId));
        CustomerPreferences preference = new CustomerPreferences(request.size());
        customer.addPreference(preference);
        //note that we are not doing repository.save(...) since parent is manged, cascade is configured, and transaction is active
        return new CustomerPreferenceResponse(preference.getPreferenceId(), preference.getCustomerSize());
    }

    public ProfileResponse getCustomerDetails(UUID customerId) {
        CustomerProfile profile = repository.findById(customerId).orElseThrow(() -> new ProfileNotFoundException(customerId));
        List<CustomerPreferenceResponse> responsePreferences = profile.getPreferences().stream().map(ep -> 
             new CustomerPreferenceResponse(ep.getPreferenceId(), ep.getCustomerSize())
        ).toList();
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), responsePreferences);
    }

    public List<ProfileResponse> getAllCustomerDetails_JPQL() {
        List<CustomerProfile> profiles = repository.findAllDetails_JPQL();
        return profiles.stream().map(profile -> {
            List<CustomerPreferenceResponse> prResponse = 
            profile.getPreferences().stream()
            .map(pr -> 
                new CustomerPreferenceResponse(pr.getPreferenceId(), pr.getCustomerSize())
            ).toList();
            return new ProfileResponse(
                profile.getCustomerId(), 
                profile.getFirstName(), 
                profile.getLastName(), 
                profile.getEmail(), 
                prResponse
            );
        }).toList();
    }

    public List<ProfileResponse> getAllCustomerDetails_EntityGraph() {
        List<CustomerProfile> profiles = repository.findAllBy();
        return profiles.stream().map(profile -> {
            List<CustomerPreferenceResponse> prResponse = 
            profile.getPreferences().stream()
            .map(pr -> 
                new CustomerPreferenceResponse(pr.getPreferenceId(), pr.getCustomerSize())
            ).toList();
            return new ProfileResponse(
                profile.getCustomerId(), 
                profile.getFirstName(), 
                profile.getLastName(), 
                profile.getEmail(), 
                prResponse
            );
        }).toList();
    }

    @Transactional
    public String deletePreference(UUID customerId, int preferenceId) {

        CustomerProfile customer = repository.findById(customerId).orElseThrow( () -> new ProfileNotFoundException(customerId));
        List<CustomerPreferences> preferences = customer.getPreferences();

        CustomerPreferences preferenceToDelete = 
            preferences
            .stream()
            .filter(p -> p.getPreferenceId() == preferenceId)
            .findFirst()
            .orElseThrow(() -> new PreferenceNotFoundException(preferenceId));

        customer.deletePreference(preferenceToDelete);
        return "deleted successfully";
    }

    @Transactional(rollbackFor = Exception.class)
    public ProfileResponse updateProfileAndPreferences(UUID customerId, UpdateProfileAndPreferenceRequest profileAndPreferenceRequest) throws Exception{

        CustomerProfile customerProfile = repository.findById(customerId).orElseThrow( () -> new ProfileNotFoundException(customerId));

        customerProfile.setFirstName(profileAndPreferenceRequest.profileRequest().firstName());
        customerProfile.setLastName(profileAndPreferenceRequest.profileRequest().lastName());
        customerProfile.setEmail(profileAndPreferenceRequest.profileRequest().email());

        customerProfile.getPreferences().clear();

        for(PreferenceRequest prefRequest: profileAndPreferenceRequest.preferenceRequests()) {
            customerProfile.addPreference(new CustomerPreferences(prefRequest.size()));
        }

        //throwing CheckedException, need to declare in the function as well -> public method() throws Exception {}
        if (true)
            throw new Exception("checked exception");

        auditRepository.save(new ProfileAudit(customerId, "PROFILE_UPDATED", Instant.now()));

        List<CustomerPreferenceResponse> prResponse = 
            customerProfile
            .getPreferences()
            .stream()
            .map(pr -> 
                new CustomerPreferenceResponse(
                    pr.getPreferenceId(), 
                    pr.getCustomerSize()
                ))
            .toList();

        //Checked exceptions do not automatically trigger rollback by default. Exceptions and error rollback by default.
        //throw new RuntimeException("Simulation");

       return new ProfileResponse(
            customerProfile.getCustomerId(), 
            customerProfile.getFirstName(), 
            customerProfile.getLastName(), 
            customerProfile.getEmail(), 
            prResponse
        );
    }
}
