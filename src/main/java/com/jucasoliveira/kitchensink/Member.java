package com.jucasoliveira.kitchensink;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Document("members")
public record Member(@Id String id, @NotBlank String name, @Email String email, Address address) {
    public record Address(String street, String city) {
    }
}
