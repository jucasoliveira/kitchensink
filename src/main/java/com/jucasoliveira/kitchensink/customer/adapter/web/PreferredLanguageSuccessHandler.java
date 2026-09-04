package com.jucasoliveira.kitchensink.customer.adapter.web;

import java.io.IOException;

import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SignOnNotifier.java:138-140 — signing on set the session locale from the stored profile.
 *
 * <p>It lives in {@code adapter.web} rather than {@code adapter.security} because its whole API is
 * servlet types, and {@code LayeringRulesTest.the_web_tier_lives_in_the_web_adapter} exempts only
 * the web adapter. {@code CustomerUserDetailsService} stays in {@code adapter.security}: it touches
 * Spring Security core and no servlet API.
 */
@Component
@Profile("mongo")
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
