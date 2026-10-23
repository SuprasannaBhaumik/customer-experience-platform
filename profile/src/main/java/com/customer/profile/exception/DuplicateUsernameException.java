package com.customer.profile.exception;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("Profile cannot be created with username:" + username + " as it already exists");
    }

}
