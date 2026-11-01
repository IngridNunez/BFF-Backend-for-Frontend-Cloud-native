package com.sanosysalvos.bff;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sanosysalvos.bff.service.ProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chat.ticket-secret=secreto-de-prueba-de-al-menos-32-caracteres!!",
        "chat.ws-url=ws://localhost:8084/api/v1/ws/chat"
})
@AutoConfigureMockMvc
class ChatTicketControllerIntegrationTest {

    private static final String COOKIES = "access_token=access-token; id_token=id-token; refresh_token=refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private NimbusJwtDecoder jwtDecoder;

    @MockitoBean
    private NimbusJwtDecoder idTokenDecoder;

    private Jwt jwt(String sub) {
        return Jwt.withTokenValue("token-" + sub)
                .header("alg", "none")
                .subject(sub)
                .claim("sub", sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void conSesionEmiteUnTicketFirmadoConElSubDelAccessToken() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(jwt("usuario-1"));
        when(idTokenDecoder.decode(any())).thenReturn(jwt("usuario-1"));

        MvcResult res = mockMvc.perform(post("/api/v1/chat/ticket")
                        .header("Cookie", COOKIES)
                        .header("X-Requested-With", "web"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.wsUrl").value("ws://localhost:8084/api/v1/ws/chat"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(30))
                .andReturn();

        String ticket = JsonPath.read(res.getResponse().getContentAsString(), "$.ticket");
        SignedJWT jwt = SignedJWT.parse(ticket);
        assertTrue(jwt.verify(new MACVerifier(
                "secreto-de-prueba-de-al-menos-32-caracteres!!".getBytes(StandardCharsets.UTF_8))));
        assertEquals("usuario-1", jwt.getJWTClaimsSet().getSubject());
        assertEquals("ms-chat", jwt.getJWTClaimsSet().getAudience().get(0));
        /* lo resuelve el bff: no se reenvia a ningun microservicio */
        verifyNoInteractions(proxyService);
    }

    @Test
    void sinSesionDevuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/chat/ticket").header("X-Requested-With", "web"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(proxyService);
    }

    @Test
    void sinHeaderAntiCsrfDevuelve403() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(jwt("usuario-1"));
        when(idTokenDecoder.decode(any())).thenReturn(jwt("usuario-1"));

        mockMvc.perform(post("/api/v1/chat/ticket").header("Cookie", COOKIES))
                .andExpect(status().isForbidden());
    }

    @Test
    void conTokensQueNoCoincidenEnSubDevuelve401() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(jwt("usuario-access"));
        when(idTokenDecoder.decode(any())).thenReturn(jwt("usuario-id"));

        mockMvc.perform(post("/api/v1/chat/ticket")
                        .header("Cookie", COOKIES)
                        .header("X-Requested-With", "web"))
                .andExpect(status().isUnauthorized());
    }
}
