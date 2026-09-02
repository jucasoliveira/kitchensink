package com.jucasoliveira.kitchensink.customer.domain;

public record Customer(String userId, Account account) {
    public static Customer register(String userId, ContactInfo contactInfo) {
        return new Customer(userId, new Account(AccountStatus.ACTIVE, contactInfo));
    }
}