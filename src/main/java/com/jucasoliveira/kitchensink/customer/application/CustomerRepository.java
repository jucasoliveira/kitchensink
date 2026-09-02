package com.jucasoliveira.kitchensink.customer.application;

import java.util.Optional;

import com.jucasoliveira.kitchensink.customer.domain.Customer;

public interface CustomerRepository {
    Customer save(Customer customer);

    Optional<Customer> findByUserId(String userId);
}
