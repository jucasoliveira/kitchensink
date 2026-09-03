package com.jucasoliveira.kitchensink.customer.adapter.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.RegisterCustomerCommand;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@Profile("mongo")
@RequestMapping("/customers")
public class CustomerController {
    private static final RegisterCustomerCommand BLANK = new RegisterCustomerCommand("", "",
            new ContactInfo("", "", "", "", new Address("", "", "", "", "", "")));

    private final CustomerRegistration registration;

    CustomerController(CustomerRegistration registration) {
        this.registration = registration;
    }

    @GetMapping
    String page(Model model) {
        return page(model, BLANK);
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("command") RegisterCustomerCommand command, BindingResult binding,
            Model model) {
        if (binding.hasErrors()) {
            return page(model, command);
        }
        registration.register(command);
        return "redirect:/customers";
    }

    private String page(Model model, RegisterCustomerCommand command) {
        model.addAttribute("command", command);
        model.addAttribute("customers", registration.registered());
        return "customers";
    }

}
