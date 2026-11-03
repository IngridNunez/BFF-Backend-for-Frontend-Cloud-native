package com.sanosysalvos.bff.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/*
 * El WebSocket del chat no puede pasar por el bff ni por API Gateway (HTTP API), y el navegador
 * tampoco puede mandar la cookie de sesion a otro origen. Por eso el bff, que ya valido la sesion,
 * firma un ticket HS256 de vida muy corta con el sub del usuario; ms-chat lo verifica con el mismo
 * secreto (CHAT_TICKET_SECRET) y lo acepta una sola vez (jti).
 * Falla cerrado: sin secreto de al menos 32 caracteres no se emite nada.
 */
@Service
public class ChatTicketService {

    public static final String AUDIENCIA = "ms-chat"; /* debe coincidir con TicketValidator de ms-chat */
    static final Duration VIGENCIA = Duration.ofSeconds(30);
    private static final int LARGO_MINIMO_SECRETO = 32;

    private final byte[] secreto;
    private final String wsUrl;
    private final Clock clock;

    @Autowired
    public ChatTicketService(@Value("${chat.ticket-secret:}") String secreto,
                             @Value("${chat.ws-url:}") String wsUrl) {
        this(secreto, wsUrl, Clock.systemUTC());
    }

    ChatTicketService(String secreto, String wsUrl, Clock clock) {
        this.secreto = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        this.wsUrl = wsUrl == null ? "" : wsUrl.trim();
        this.clock = clock;
    }

    public boolean configurado() {
        return secreto.length >= LARGO_MINIMO_SECRETO && !wsUrl.isEmpty();
    }

    public String wsUrl() {
        return wsUrl;
    }

    public long vigenciaSegundos() {
        return VIGENCIA.toSeconds();
    }

    /* vacio si el chat no esta configurado; el ticket nunca se loguea */
    public Optional<String> emitir(String sub) {
        if (!configurado() || sub == null || sub.isBlank()) {
            return Optional.empty();
        }
        Instant ahora = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .audience(AUDIENCIA)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plus(VIGENCIA)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secreto));
            return Optional.of(jwt.serialize());
        } catch (JOSEException e) {
            return Optional.empty();
        }
    }
}
