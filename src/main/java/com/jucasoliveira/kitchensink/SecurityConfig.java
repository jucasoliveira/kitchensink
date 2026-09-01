package com.jucasoliveira.kitchensink;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(
                        a -> a.requestMatchers("/", "/actuator/health").permitAll().anyRequest().authenticated())
                .formLogin(Customizer.withDefaults()).build();
    }
}
