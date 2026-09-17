package com.sanosysalvos.bff.config;

import com.sanosysalvos.bff.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean /* configura que rutas son publicas y cuales requieren autenticacion y en que orden se ejecutan los filtros */
    public SecurityFilterChain securityFilterChain(HttpSecurity http, NimbusJwtDecoder jwtDecoder) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/mascotas", "/api/v1/mascotas/**").permitAll() /* listado y detalle público */
                .requestMatchers(HttpMethod.GET, "/api/v1/alertas/zona").permitAll() /* búsqueda de alertas pública */
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2 /* configura el servidor de recursos OAuth2 para usar JWT */
                .jwt(jwt -> jwt.decoder(jwtDecoder)) /* configura el decoder de JWT */
            )
            .addFilterAfter(jwtFilter, AuthorizationFilter.class); /* ejecutar después de que Spring Security ya decidió si la ruta es pública o requiere autenticación, y ya autenticó el access token */

        return http.build();
    }
}