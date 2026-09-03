package com.jucasoliveira.kitchensink.customer.adapter.web;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.CustomerResponse;
import com.jucasoliveira.kitchensink.customer.application.DuplicateAccountException;
import com.jucasoliveira.kitchensink.customer.application.RegisterCustomerCommand;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@Profile("mongo")
@RequestMapping(path = "/api/customers", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerResource {
    private final CustomerRegistration registration;

    CustomerResource(CustomerRegistration registration) {
        this.registration = registration;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse register(@Valid @RequestBody RegisterCustomerCommand command) {
        return CustomerResponse.from(registration.register(command));
    }

    @GetMapping
    List<CustomerResponse> list() {
        return registration.registered().stream().map(CustomerResponse::from).toList();
    }

    @ExceptionHandler(DuplicateAccountException.class)
    ProblemDetail duplicateAccount(DuplicateAccountException taken) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The user name you have chosen is in use. Please choose another username.");
        problem.setTitle("Duplicate Account");
        problem.setProperty("userId", taken.userId());
        return problem;
    }

}
