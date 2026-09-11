package com.customer.profile.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Profile cannot be created with email:" + email + " as it already exists");
    }

}
