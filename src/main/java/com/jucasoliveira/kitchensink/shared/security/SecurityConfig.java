package com.jucasoliveira.kitchensink.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {
    private static final String[] SIGNON_CONFIG_PROTECTED = {
            "/customers/me",
            "/customers/me/profile"
    };

    private static final String[] PUBLIC = { "/", "/actuator/health", "/customers", "/catalog", "/catalog/**",
            "/api/catalog/**", "/css/**", "/images/**" };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<AuthenticationSuccessHandler> signOnSuccess)
            throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(SIGNON_CONFIG_PROTECTED).authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> signOnSuccess.ifAvailable(form::successHandler)).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
