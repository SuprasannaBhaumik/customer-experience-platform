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

/**
 * Handles customer profile operations and maps persisted entities to response DTOs.
 * Basic profile responses omit preferences; detail responses include them.
 * Transactional preference operations rely on JPA change tracking and the
 * cascade/orphan-removal settings on CustomerProfile.
 */
@Service
public class ProfileService {

    // Selects the repository bean named "myChoosenRepo" explicitly for injection.
    @Autowired
    @Qualifier("myChoosenRepo")
    private ProfileRepository repository;

    @Autowired 
    private ProfileAuditRepository auditRepository;

    /** Creates a profile after checking for an existing email, ignoring letter case. */
    public ProfileResponse saveProfile(CreateProfileRequest request) {
        if (repository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new DuplicateEmailException(request.email());
        }
        CustomerProfile profile = repository
                .save(new CustomerProfile(request.firstName(), request.lastName(), request.email()));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), null);
    }

    /** Returns basic profile fields, or throws if the customer does not exist. */
    public ProfileResponse getProfile(UUID customerId) {
        CustomerProfile profile = repository.findById(customerId)
                .orElseThrow(() -> new ProfileNotFoundException(customerId));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), null);
    }

    /** Looks up basic profile fields using the repository's email equality query. */
    public ProfileResponse findProfileByEmail(String email) {
        CustomerProfile profile = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Profile with the email not found"));
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), null);
    }

    /** Returns "failure" for a null ID; a non-null but unknown ID raises an exception. */
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

    /** Applies the supplied profile fields; returns "failure" when the ID is null. */
    public String updateCustomerProfile(UUID customerId, UpdateProfileRequest request) {
        if (customerId != null) {
            CustomerProfile customer = repository.findById(customerId)
                    .orElseThrow(() -> new ProfileNotFoundException(customerId));
            customer.setFirstName(request.firstName());
            // TODO: This calls setFirstName again, overwriting it with the last name.
            // Use setLastName here when correcting the update behavior.
            customer.setFirstName(request.lastName());
            customer.setEmail(request.email());
            repository.save(customer);
            return "success";
        }
        return "failure";
    }

    /** Updates only non-null fields; null values leave existing values unchanged. */
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


    /** Adds a preference and links both sides of the customer/preference relationship. */
    @Transactional
    public CustomerPreferenceResponse addPreference(CustomerPreferenceRequest request, UUID customerId) {
        CustomerProfile customer = repository.findById(customerId).orElseThrow(() -> new ProfileNotFoundException(customerId));
        CustomerPreferences preference = new CustomerPreferences(request.size());
        customer.addPreference(preference);
        // The loaded customer is managed within this transaction. JPA persists the
        // new preference through cascading when changes are flushed; no save is needed.
        return new CustomerPreferenceResponse(preference.getPreferenceId(), preference.getCustomerSize());
    }

    /** Returns profile fields together with the customer's preference DTOs. */
    public ProfileResponse getCustomerDetails(UUID customerId) {
        CustomerProfile profile = repository.findById(customerId).orElseThrow(() -> new ProfileNotFoundException(customerId));
        // Preferences are lazy-loaded, so accessing them requires an open persistence context.
        List<CustomerPreferenceResponse> responsePreferences = profile.getPreferences().stream().map(ep -> 
             new CustomerPreferenceResponse(ep.getPreferenceId(), ep.getCustomerSize())
        ).toList();
        return new ProfileResponse(profile.getCustomerId(), profile.getFirstName(), profile.getLastName(),
                profile.getEmail(), responsePreferences);
    }

    /** Uses a JPQL fetch join to load profiles and preferences, including profiles with none. */
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

    /** Uses the repository's entity graph to fetch preferences along with profiles. */
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

    /** Removes a preference only if it belongs to the specified customer. */
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

        // The helper unlinks both sides; orphanRemoval deletes the preference on flush.
        customer.deletePreference(preferenceToDelete);
        return "deleted successfully";
    }

    /**
     * Replaces profile fields and the complete preference list in one transaction.
     * Currently demonstrates rollback by always throwing a checked exception, so
     * the changes are rolled back and the audit/response code is never reached.
     */
    @Transactional(rollbackFor = Exception.class)
    public ProfileResponse updateProfileAndPreferences(UUID customerId, UpdateProfileAndPreferenceRequest profileAndPreferenceRequest) throws Exception{

        CustomerProfile customerProfile = repository.findById(customerId).orElseThrow( () -> new ProfileNotFoundException(customerId));

        customerProfile.setFirstName(profileAndPreferenceRequest.profileRequest().firstName());
        customerProfile.setLastName(profileAndPreferenceRequest.profileRequest().lastName());
        customerProfile.setEmail(profileAndPreferenceRequest.profileRequest().email());

        // This is replacement, not a patch: old preferences become orphans to delete.
        customerProfile.getPreferences().clear();

        for(PreferenceRequest prefRequest: profileAndPreferenceRequest.preferenceRequests()) {
            customerProfile.addPreference(new CustomerPreferences(prefRequest.size()));
        }

        // rollbackFor includes checked exceptions, which do not trigger rollback by default.
        // This unconditional exception deliberately prevents the transaction from committing.
        if (true)
            throw new Exception("checked exception");

        // If the simulation above is removed, the audit is saved in the same transaction.
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

        // RuntimeException and Error trigger rollback by default in Spring transactions.
        // Checked exceptions do not automatically trigger rollback by default.
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
