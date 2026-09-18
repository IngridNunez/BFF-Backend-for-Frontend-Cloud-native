package com.sanosysalvos.bff;

import com.sanosysalvos.bff.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * prueba de integración: levanta el contexto completo de Spring (SecurityConfig + JwtDecoderConfig +
 * JwtFilter + ProxyController) con MockMvc. Se mockean ProxyService (evita llamadas reales a los MS
 * vía Eureka) y NimbusJwtDecoder (evita descargar el JWKS real de Cognito).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProxyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private NimbusJwtDecoder jwtDecoder;

    @MockitoBean
    private NimbusJwtDecoder idTokenDecoder;

    private Jwt jwtConSubject(String subject) {
        return Jwt.withTokenValue("token-" + subject)
                .header("alg", "none")
                .subject(subject)
                .claim("sub", subject)
                .claim("token_use", "access")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void rutaProtegida_sinAuthorization_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proxyService);
    }

    @Test
    void rutaProtegida_conTokensValidosYSubsCoincidentes_llegaAlProxyServiceYDevuelve200() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(jwtConSubject("usuario-1"));
        when(idTokenDecoder.decode(any())).thenReturn(jwtConSubject("usuario-1"));
        when(proxyService.proxy(any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.ok("ok".getBytes()));

        mockMvc.perform(get("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer access-token")
                        .header("X-Id-Token", "Bearer id-token")
                        .header("X-Refresh-Token", "refresh-token"))
                .andExpect(status().isOk());
    }

    @Test
    void rutaProtegida_conAuthorizationPeroSinIdToken_devuelve401() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(jwtConSubject("usuario-1"));

        mockMvc.perform(get("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegida_conTokensQueNoCoincidenEnSub_devuelve401() throws Exception {
        when(jwtDecoder.decode("access-token")).thenReturn(jwtConSubject("usuario-access"));
        when(idTokenDecoder.decode("id-token")).thenReturn(jwtConSubject("usuario-id-distinto"));

        mockMvc.perform(get("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer access-token")
                        .header("X-Id-Token", "Bearer id-token")
                        .header("X-Refresh-Token", "refresh-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proxyService);
    }

    @Test
    void rutaPublicaMascotas_sinNingunToken_llegaAlProxyServiceYDevuelve200() throws Exception {
        when(proxyService.proxy(any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.ok("mascotas".getBytes()));

        mockMvc.perform(get("/api/v1/mascotas"))
                .andExpect(status().isOk());

        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void rutaPublicaAlertasZona_sinNingunToken_llegaAlProxyServiceYDevuelve200() throws Exception {
        when(proxyService.proxy(any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.ok("alertas".getBytes()));

        mockMvc.perform(get("/api/v1/alertas/zona"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealth_esPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
