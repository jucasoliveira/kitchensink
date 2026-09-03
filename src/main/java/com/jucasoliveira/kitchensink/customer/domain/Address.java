package com.jucasoliveira.kitchensink.customer.domain;

import jakarta.validation.constraints.NotBlank;

public record Address(@NotBlank String streetName1, String streetName2, @NotBlank String city, @NotBlank String state,
                @NotBlank String zipCode,
                String country) {
}
