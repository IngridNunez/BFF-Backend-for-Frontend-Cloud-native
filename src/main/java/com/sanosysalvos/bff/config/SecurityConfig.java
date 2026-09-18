package com.sanosysalvos.bff.config;

import com.sanosysalvos.bff.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean /* configura que rutas son publicas y cuales requieren autenticacion y en que orden se ejecutan los filtros */
    public SecurityFilterChain securityFilterChain(HttpSecurity http, NimbusJwtDecoder jwtDecoder) throws Exception {
        http
            // ###################################
            // API stateless (JWT via Authorization/X-Id-Token, sin cookies de sesion):
            // CSRF es un concepto de sesiones basadas en cookies, no aplica aca.
            // Sin esto, cualquier POST/PATCH/DELETE (incluidas las rutas publicas) da 403.
            .csrf(csrf -> csrf.disable())
            // ###################################
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/mascotas", "/api/v1/mascotas/**").permitAll() /* listado y detalle público */
                .requestMatchers(HttpMethod.GET, "/api/v1/alertas/zona").permitAll() /* búsqueda de alertas pública */
                .requestMatchers(HttpMethod.POST, "/api/v1/contactos").permitAll() /* cualquiera puede contactar por una mascota, sin cuenta */ // ###################################
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2 /* configura el servidor de recursos OAuth2 para usar JWT */
                .jwt(jwt -> jwt.decoder(jwtDecoder)) /* configura el decoder de JWT */
            )
            .addFilterAfter(jwtFilter, AuthorizationFilter.class); /* ejecutar después de que Spring Security ya decidió si la ruta es pública o requiere autenticación, y ya autenticó el access token */

        return http.build();
    }

    // ###################################
    /* permite que el frontend (localhost:5173 en dev) llame al bff desde el navegador */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "X-Id-Token", "X-Refresh-Token", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    // ###################################
}
