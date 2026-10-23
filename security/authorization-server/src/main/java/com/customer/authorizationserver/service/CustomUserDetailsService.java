package com.customer.authorizationserver.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.customer.authorizationserver.entity.CustomUser;
import com.customer.authorizationserver.repository.CustomUserDetailsRepository;

/**
 * Loads login users from the profile database. Being the only UserDetailsService bean,
 * form login picks it up automatically and checks the password with the PasswordEncoder bean.
 */
@Service 
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired 
    private CustomUserDetailsRepository customUserDetailsRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        CustomUser user = customUserDetailsRepository
            .findByUsername(username)
            // profiles created before passwords existed have no hash and cannot log in
            .filter(u -> u.getPasswordHash() != null)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // roles() adds the ROLE_ prefix, which the token customizer strips again for the roles claim
        UserDetails details = User.withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .roles(user.getRoles().toArray(String[]::new))
            .build();

        return new CustomerUserDetails(user.getCustomerId(), details.getUsername(), details.getPassword(),
            details.getAuthorities());
    }

}
