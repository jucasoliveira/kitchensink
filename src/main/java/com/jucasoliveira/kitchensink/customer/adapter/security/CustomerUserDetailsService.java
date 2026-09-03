package com.jucasoliveira.kitchensink.customer.adapter.security;

import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@Profile("mongo")
class CustomerUserDetailsService implements UserDetailsService {
    private final CustomerRepository customers;

    CustomerUserDetailsService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public UserDetails loadUserByUsername(String userId) {
        return customers.findByUserId(userId)
                .map(c -> User.withUsername(c.userId()).password(c.passwordHash().value()).roles("CUSTOMER").build())
                .orElseThrow(() -> new UsernameNotFoundException(userId));
    }
}