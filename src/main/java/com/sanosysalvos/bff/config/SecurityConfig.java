package com.sanosysalvos.bff.config;

import com.sanosysalvos.bff.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
                /* TODO: validar aud (audience) cuando tengamos el Client ID de Cognito en AWS
                * verifica que el token fue emitido para esta app y no para otra
                * .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                */

            )
            .addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter.class); /* ejecutar después de Spring Security */

        return http.build();
    }
}