package com.jucasoliveira.kitchensink.customer.application;

public class DuplicateAccountException extends RuntimeException {

    private final String userId;

    public DuplicateAccountException(String userId) {
        super("user id already registered: " + userId);
        this.userId = userId;
    }

    public String userId() {
        return this.userId;
    }
}