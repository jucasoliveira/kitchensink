package com.jucasoliveira.kitchensink.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<AuthenticationSuccessHandler> signOnSuccess)
            throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(
                        a -> a.requestMatchers("/", "/actuator/health", "/customers", "/api/customers", "/catalog",
                                "/catalog/**", "/css/**", "/images/**", "/api/catalog/**").permitAll()
                                .anyRequest().authenticated())
                .formLogin(form -> signOnSuccess.ifAvailable(form::successHandler)).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
