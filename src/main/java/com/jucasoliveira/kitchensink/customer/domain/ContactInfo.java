package com.jucasoliveira.kitchensink.customer.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContactInfo(@NotBlank String givenName, @NotBlank String familyName, @NotBlank String telephone,
        @Email String email, @NotNull @Valid Address address) {
}
