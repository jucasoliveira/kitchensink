package com.jucasoliveira.kitchensink.customer.adapter.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
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

    @GetMapping("/{userId}")
    CustomerResponse byId(@PathVariable String userId) {
        return this.registration.byUserId(userId).map(CustomerResponse::from)
                .orElseThrow(() -> new UnknownCustomerException(userId));
    }

    @ExceptionHandler(UnknownCustomerException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail unknownCustomer(UnknownCustomerException missing) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such customer.");
        problem.setTitle("Unknown Customer");
        problem.setProperty("userId", missing.userId);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail invalidRegistration(MethodArgumentNotValidException violations) {
        Map<String, String> errors = new LinkedHashMap<>();
        violations.getBindingResult().getFieldErrors()
                .forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The registration was rejected before it reached the store.");
        problem.setTitle("Validation Failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    static final class UnknownCustomerException extends RuntimeException {
        private final String userId;

        UnknownCustomerException(String userId) {
            super(userId);
            this.userId = userId;
        }
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
