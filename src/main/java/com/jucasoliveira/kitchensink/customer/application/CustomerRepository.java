package com.jucasoliveira.kitchensink.customer.application;

import java.util.List;
import java.util.Optional;

import com.jucasoliveira.kitchensink.customer.domain.Customer;

public interface CustomerRepository {
    Customer add(Customer customer);

    Customer update(Customer customer);

    Optional<Customer> findByUserId(String userId);

    List<Customer> findAll();

}
