package com.sanosysalvos.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JwtDecoderConfigTest {

    /* host que no existe: si crear el bean hiciera una llamada de red (como JwtDecoders.fromIssuerLocation),
     * esto fallaría y el BFF no arrancaría cuando Cognito no responde o las variables están vacías */
    private static final String ISSUER = "https://cognito-idp.no-existe.invalid/pool";
    private static final String JWKS = "https://cognito-idp.no-existe.invalid/pool/.well-known/jwks.json";

    private final JwtDecoderConfig config = new JwtDecoderConfig();

    @Test
    void crearElDecoderDeAccessTokenNoHaceLlamadasDeRed() {
        NimbusJwtDecoder decoder = assertDoesNotThrow(() -> config.jwtDecoder(ISSUER, JWKS, "client-id"));

        assertThat(decoder).isNotNull();
    }

    @Test
    void crearElDecoderDeIdTokenNoHaceLlamadasDeRed() {
        NimbusJwtDecoder decoder = assertDoesNotThrow(() -> config.idTokenDecoder(ISSUER, JWKS, "client-id"));

        assertThat(decoder).isNotNull();
    }

    @Test
    void unTokenQueNoEsUnJwtSeRechazaSinNecesitarLasClavesDeCognito() {
        NimbusJwtDecoder decoder = config.jwtDecoder(ISSUER, JWKS, "client-id");

        assertThatThrownBy(() -> decoder.decode("esto-no-es-un-jwt")).isInstanceOf(JwtException.class);
    }
}
