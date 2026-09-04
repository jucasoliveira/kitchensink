package com.jucasoliveira.kitchensink.customer.adapter.web;

import java.security.Principal;
import java.util.List;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.DuplicateAccountException;
import com.jucasoliveira.kitchensink.customer.application.RegisterCustomerCommand;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequestMapping("/customers")
public class CustomerController {
    private static final RegisterCustomerCommand BLANK = new RegisterCustomerCommand("", "",
            new ContactInfo("", "", "", "", new Address("", "", "", "", "", "")));

    private final CustomerRegistration registration;
    private final LocaleResolver locales;

    CustomerController(CustomerRegistration registration, LocaleResolver locales) {
        this.registration = registration;
        this.locales = locales;
    }

    @GetMapping
    String page(Model model, Principal principal) {
        return page(model, BLANK, principal);
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("command") RegisterCustomerCommand command, BindingResult binding,
            Model model, Principal principal) {
        if (binding.hasErrors()) {
            return page(model, command, principal);
        }
        registration.register(command);
        return "redirect:/customers";
    }

    private String page(Model model, RegisterCustomerCommand command, Principal principal) {
        model.addAttribute("command", command);
        model.addAttribute("customers", principal == null ? List.of() : registration.registered());
        return "customers";
    }

    @GetMapping("/me")
    String account(Principal principal, Model model) {
        model.addAttribute("customer", registration.byUserId(principal.getName()).orElseThrow());
        return "customers/account";
    }

    @PostMapping("/me/profile")
    String updateProfile(Principal principal, @RequestParam String preferredLanguage,
            @RequestParam(required = false) String favoriteCategory,
            HttpServletRequest request, HttpServletResponse response) {
        this.registration.updateProfile(principal.getName(), preferredLanguage, favoriteCategory);
        this.locales.setLocale(request, response, StringUtils.parseLocale(preferredLanguage));
        return "redirect:/customers/me";
    }

    @ExceptionHandler(DuplicateAccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String duplicateAccount() {
        return "customers/duplicate-account";
    }

    private String page(Model model, RegisterCustomerCommand command) {
        model.addAttribute("command", command);
        model.addAttribute("customers", registration.registered());
        return "customers";
    }

}
