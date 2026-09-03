package com.jucasoliveira.kitchensink.customer.application;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.PasswordHash;

import jakarta.validation.Valid;

@Service
@Validated
@Profile("mongo")
public class CustomerRegistration {
    private final CustomerRepository customers;
    private final PasswordEncoder encoder;

    CustomerRegistration(CustomerRepository customers, PasswordEncoder encoder) {
        this.customers = customers;
        this.encoder = encoder;
    }

    public Customer register(@Valid RegisterCustomerCommand command) {
        PasswordHash hash = new PasswordHash(encoder.encode(command.password()));
        return customers.save(Customer.register(command.userId(), hash, command.contactInfo()));
    }

    public List<Customer> registered() {
        return customers.findAll();
    }
}
