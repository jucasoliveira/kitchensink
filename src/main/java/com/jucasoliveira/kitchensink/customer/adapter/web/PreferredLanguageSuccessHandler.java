package com.jucasoliveira.kitchensink.customer.adapter.web;

import java.io.IOException;

import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PreferredLanguageSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    private final CustomerRepository customers;
    private final LocaleResolver locales;

    PreferredLanguageSuccessHandler(CustomerRepository customers, LocaleResolver locales) {
        this.customers = customers;
        this.locales = locales;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        this.customers.findByUserId(authentication.getName())
                .map(customer -> customer.profile().preferredLanguage())
                .map(StringUtils::parseLocale)
                .ifPresent(locale -> this.locales.setLocale(request, response, locale));
        super.onAuthenticationSuccess(request, response, authentication);
    }

}
