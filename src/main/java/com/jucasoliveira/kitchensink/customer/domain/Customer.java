package com.jucasoliveira.kitchensink.customer.domain;

public record Customer(String userId, PasswordHash passwordHash, Account account, Profile profile) {
    public static Customer register(String userId, PasswordHash passwordHash, ContactInfo contactInfo) {
        return new Customer(userId, passwordHash, new Account(AccountStatus.ACTIVE, contactInfo, CreditCard.EMPTY),
                Profile.DEFAULT);
    }
}