package com.customer.profile.exception;

/**
 * PreferenceNotFoundException
 */
public class PreferenceNotFoundException extends RuntimeException {

    public PreferenceNotFoundException(int preferenceId) {
        super("Preference not found for customer with preferenceId:" + preferenceId);
    }
}
