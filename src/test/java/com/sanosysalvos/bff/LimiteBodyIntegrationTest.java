package com.sanosysalvos.bff;

import com.sanosysalvos.bff.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* el filtro de tamaño está cableado en la cadena real de seguridad: corta incluso rutas públicas (POST /contactos) */
@SpringBootTest(properties = "bff.max-body-bytes=2048")
@AutoConfigureMockMvc
class LimiteBodyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private NimbusJwtDecoder jwtDecoder;

    @MockitoBean
    private NimbusJwtDecoder idTokenDecoder;

    @Test
    void unBodyEnormeEnUnaRutaPublicaSeRechazaConPayloadTooLargeSinLlegarAlProxy() throws Exception {
        mockMvc.perform(post("/api/v1/contactos")
                        .header("X-Requested-With", "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[3000]))
                .andExpect(status().isPayloadTooLarge());

        verifyNoInteractions(proxyService);
    }

    @Test
    void unBodyDentroDelLimiteLlegaAlProxy() throws Exception {
        when(proxyService.proxy(any(HttpServletRequest.class), any())).thenReturn(ResponseEntity.ok("ok".getBytes()));

        mockMvc.perform(post("/api/v1/contactos")
                        .header("X-Requested-With", "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[1000]))
                .andExpect(status().isOk());
    }
}
