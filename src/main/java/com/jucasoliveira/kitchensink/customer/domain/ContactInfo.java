package com.jucasoliveira.kitchensink.customer.domain;

public record ContactInfo(String givenName, String familyName, String telephone, String email, Address address) {
}
