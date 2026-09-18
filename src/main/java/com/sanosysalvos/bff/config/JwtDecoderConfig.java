package com.sanosysalvos.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/*
 * separado de SecurityConfig para evitar el ciclo: SecurityConfig -> JwtFilter -> JwtDecoder -> SecurityConfig
 */
@Configuration
public class JwtDecoderConfig {

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

    @Bean /* decoder separado para el X-Id-Token: un id token de Cognito trae token_use="id" y el client-id
            * viaja en el claim "aud" (no "client_id"), por lo que no puede validarse con el decoder de arriba
          */
    public NimbusJwtDecoder idTokenDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${cognito.client-id}") String clientId) {

        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            boolean correctUse = "id".equals(token.getClaimAsString("token_use")); /* valida que el token sea de tipo id */
            boolean correctAudience = token.getAudience() != null && token.getAudience().contains(clientId); /* valida que el token sea de nuestro cliente */
            if (correctUse && correctAudience) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "El id token no fue emitido para esta aplicación", null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
