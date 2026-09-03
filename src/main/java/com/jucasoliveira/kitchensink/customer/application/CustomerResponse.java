package com.jucasoliveira.kitchensink.customer.application;

import com.jucasoliveira.kitchensink.customer.domain.AccountStatus;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.Customer;

public record CustomerResponse(String userId, AccountStatus status, ContactInfo contactInfo) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.userId(), customer.account().status(),
                customer.account().contactInfo());
    }
}
