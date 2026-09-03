package com.jucasoliveira.kitchensink.customer.application;

import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;

import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCustomerCommand(@NotBlank @Size(max = 25) @Pattern(regexp = "[^%*]*") String userId,
        @NotBlank @Size(max = 25) String password, @Nonnull @Valid ContactInfo contactInfo) {

}
