package com.sanosysalvos.bff.config;

import com.sanosysalvos.bff.security.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
            .addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter.class); /* ejecutar después de Spring Security */

        return http.build();
    }

    @Bean /* configura el decoder de Spring Security para validar los tokens JWT emitidos por Cognito
            * valida que el token sea de tipo access y que el client_id coincida con el de la aplicacion
          */
    public NimbusJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${cognito.client-id}") String clientId) {

        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer); /* descarga jwks de cognito y valida firma */

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer); /* valida que el token sea de nuestro cognito */

        OAuth2TokenValidator<Jwt> clientIdValidator = token -> {
            boolean correctUse = "access".equals(token.getClaimAsString("token_use")); /* valida que el token sea de tipo access */
            boolean correctClient = clientId.equals(token.getClaimAsString("client_id")); /* valida que el token sea de nuestro cliente */
            if (correctUse && correctClient) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "El token no fue emitido para esta aplicación", null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, clientIdValidator)); /* encadena ambas validaciones */
        return decoder;
    }
}