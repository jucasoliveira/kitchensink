package com.jucasoliveira.kitchensink.customer.application;

import java.util.List;
import java.util.Optional;

import com.jucasoliveira.kitchensink.customer.domain.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.PasswordHash;

import jakarta.validation.Valid;

@Service
@Validated
@org.springframework.context.annotation.Profile("mongo")
public class CustomerRegistration {
    private final CustomerRepository customers;
    private final PasswordEncoder encoder;

    CustomerRegistration(CustomerRepository customers, PasswordEncoder encoder) {
        this.customers = customers;
        this.encoder = encoder;
    }

    public Customer register(@Valid RegisterCustomerCommand command) {
        if (this.customers.findByUserId(command.userId()).isPresent()) {
            throw new DuplicateAccountException(command.userId());
        }

        PasswordHash hash = new PasswordHash(this.encoder.encode(command.password()));
        return this.customers.add(Customer.register(command.userId(), hash, command.contactInfo()));
    }

    public Optional<Customer> byUserId(String userId) {
        return this.customers.findByUserId(userId);
    }

    public Customer updateProfile(String userId, String preferredLanguage, String favoriteCategory) {
        Customer customer = this.customers.findByUserId(userId).orElseThrow();
        Profile current = customer.profile();
        return this.customers.update(new Customer(customer.userId(), customer.passwordHash(), customer.account(),
                new Profile(preferredLanguage, favoriteCategory, current.myListPreference(),
                        current.bannerPreference())));
    }

    public List<Customer> registered() {
        return customers.findAll();
    }
}
