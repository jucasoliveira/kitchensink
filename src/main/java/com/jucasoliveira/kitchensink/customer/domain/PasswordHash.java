package com.jucasoliveira.kitchensink.customer.domain;

import java.util.regex.Pattern;

public record PasswordHash(String value) {
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    public PasswordHash {
        if (value == null || !BCRYPT.matcher(value).matches()) {
            throw new IllegalArgumentException("not a BCrypt hash");
        }

    }

    @Override
    public String toString() {
        return "PasswordHash[***]";
    }
}
