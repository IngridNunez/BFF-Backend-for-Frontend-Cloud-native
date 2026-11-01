package com.sanosysalvos.bff;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sanosysalvos.bff.service.ChatTicketService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChatTicketServiceTest {

    private static final String SECRETO = "secreto-de-prueba-de-al-menos-32-caracteres!!";
    private static final String WS = "ws://localhost:8084/api/v1/ws/chat";
    private static final Instant AHORA = Instant.parse("2026-10-30T12:00:00Z");

    private ChatTicketService servicio(String secreto, String ws) throws Exception {
        /* el constructor con Clock es de paquete: se usa por reflexion para fijar la hora */
        Constructor<ChatTicketService> c = ChatTicketService.class
                .getDeclaredConstructor(String.class, String.class, Clock.class);
        c.setAccessible(true);
        return c.newInstance(secreto, ws, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void emiteUnTicketHs256ConSubAudienciaVigenciaCortaYJtiUnico() throws Exception {
        ChatTicketService s = servicio(SECRETO, WS);

        String t1 = s.emitir("sub-123").orElseThrow();
        String t2 = s.emitir("sub-123").orElseThrow();

        SignedJWT jwt = SignedJWT.parse(t1);
        assertEquals(JWSAlgorithm.HS256, jwt.getHeader().getAlgorithm());
        assertTrue(jwt.verify(new MACVerifier(SECRETO.getBytes(StandardCharsets.UTF_8))));
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertEquals("sub-123", claims.getSubject());
        assertEquals("ms-chat", claims.getAudience().get(0));
        assertEquals(AHORA.plusSeconds(30), claims.getExpirationTime().toInstant());
        assertNotEquals(claims.getJWTID(), SignedJWT.parse(t2).getJWTClaimsSet().getJWTID());
    }

    @Test
    void conOtroSecretoLaFirmaNoVerifica() throws Exception {
        String t = servicio(SECRETO, WS).emitir("sub-1").orElseThrow();
        byte[] otro = "otro-secreto-distinto-de-32-caracteres-xx".getBytes(StandardCharsets.UTF_8);

        assertFalse(SignedJWT.parse(t).verify(new MACVerifier(otro)));
    }

    @Test
    void sinSecretoOConSecretoCortoNoEmiteNada() throws Exception {
        assertEquals(Optional.empty(), servicio("", WS).emitir("sub-1"));
        assertEquals(Optional.empty(), servicio(null, WS).emitir("sub-1"));
        assertEquals(Optional.empty(), servicio("corto", WS).emitir("sub-1"));
        assertFalse(servicio("corto", WS).configurado());
    }

    @Test
    void sinUrlDelWebSocketElChatNoEstaConfigurado() throws Exception {
        assertFalse(servicio(SECRETO, "").configurado());
        assertEquals(Optional.empty(), servicio(SECRETO, "  ").emitir("sub-1"));
    }

    @Test
    void sinSubNoEmite() throws Exception {
        ChatTicketService s = servicio(SECRETO, WS);

        assertEquals(Optional.empty(), s.emitir(null));
        assertEquals(Optional.empty(), s.emitir("  "));
    }
}
