package com.jucasoliveira.kitchensink.customer.domain;

public record Account(AccountStatus status, ContactInfo contactInfo, CreditCard creditCard) {
}
