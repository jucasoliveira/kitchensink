package com.jucasoliveira.kitchensink.customer.application;

import com.jucasoliveira.kitchensink.customer.domain.AccountStatus;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.Profile;

public record CustomerResponse(String userId, AccountStatus status, ContactInfo contactInfo, Profile profile) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.userId(), customer.account().status(),
                customer.account().contactInfo(), customer.profile());
    }
}
