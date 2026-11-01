package com.sanosysalvos.bff;

import com.sanosysalvos.bff.service.ProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* sin CHAT_TICKET_SECRET / CHAT_WS_URL el endpoint falla cerrado con 503, nunca emite un ticket debil */
@SpringBootTest
@AutoConfigureMockMvc
class ChatTicketSinConfigurarIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private NimbusJwtDecoder jwtDecoder;

    @MockitoBean
    private NimbusJwtDecoder idTokenDecoder;

    @Test
    void sinConfiguracionDelChatDevuelve503() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("usuario-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(jwtDecoder.decode(any())).thenReturn(jwt);
        when(idTokenDecoder.decode(any())).thenReturn(jwt);

        mockMvc.perform(post("/api/v1/chat/ticket")
                        .header("Cookie", "access_token=a; id_token=i; refresh_token=r")
                        .header("X-Requested-With", "web"))
                .andExpect(status().isServiceUnavailable());
    }
}
