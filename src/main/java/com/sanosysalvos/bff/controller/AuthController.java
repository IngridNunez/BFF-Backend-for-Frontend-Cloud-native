package com.sanosysalvos.bff.controller;

import com.sanosysalvos.bff.dto.SesionRequestDto;
import com.sanosysalvos.bff.dto.UsuarioSesionDto;
import com.sanosysalvos.bff.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/*
 * Maneja el ciclo de vida de la sesion (entrar / seguir logueado / salir) via
 * cookies httpOnly, para que el frontend deje de guardar los tokens en
 * localStorage. Controller separado del ProxyController a proposito: el bff
 * no necesita ser el unico punto de entrada del sistema (eso es rol del API
 * Manager, que va delante del bff), asi que puede tener las rutas internas
 * que necesite.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtDecoder jwtDecoder; /* valida access token (token_use=access) */
    private final JwtDecoder idTokenDecoder; /* valida id token (token_use=id) */
    private final boolean cookiesSeguras; /* fuerza el atributo Secure en las cookies de sesión, ver bff.cookies.secure */

    /* constructor explícito para poder usar @Qualifier (Lombok no copia el @Qualifier a los parámetros) */
    public AuthController(
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            @Qualifier("idTokenDecoder") JwtDecoder idTokenDecoder,
            /* Detrás de API Gateway/ALB el TLS se termina antes del bff y request.isSecure() da false aunque el
             * navegador use https: la cookie saldría sin Secure. En producción se define BFF_COOKIES_SECURE=true.
             * En local (http://localhost) queda en false, porque el navegador descarta en silencio una cookie
             * Secure recibida por http. */
            @Value("${bff.cookies.secure:false}") boolean cookiesSeguras) {
        this.jwtDecoder = jwtDecoder;
        this.idTokenDecoder = idTokenDecoder;
        this.cookiesSeguras = cookiesSeguras;
    }

    @PostMapping("/session")
    public ResponseEntity<Void> iniciarSesion(@Valid @RequestBody SesionRequestDto dto, HttpServletRequest request, HttpServletResponse response) {
        Jwt accessToken;
        Jwt idToken;
        try {
            accessToken = jwtDecoder.decode(dto.getAccessToken());
            idToken = idTokenDecoder.decode(dto.getIdToken());
        } catch (JwtException e) {
            /* sin stack trace ni contenido del token */
            log.warn("Token invalido al iniciar sesion ({})", e.getClass().getSimpleName());
            return ResponseEntity.status(401).build();
        }

        if (accessToken.getSubject() == null || !accessToken.getSubject().equals(idToken.getSubject())) {
            log.warn("Los subs de access token e id token no coinciden");
            return ResponseEntity.status(401).build();
        }
        if (accessToken.getExpiresAt() == null || idToken.getExpiresAt() == null) {
            return ResponseEntity.status(401).build();
        }

        Duration duracionAccessToken = Duration.between(Instant.now(), accessToken.getExpiresAt());
        Duration duracionIdToken = Duration.between(Instant.now(), idToken.getExpiresAt());

        boolean secure = esSegura(request);
        CookieUtil.setCookie(response, "access_token", dto.getAccessToken(), duracionAccessToken, secure);
        CookieUtil.setCookie(response, "id_token", dto.getIdToken(), duracionIdToken, secure);
        /* ya no se guarda el refresh_token; si quedó una cookie de una sesión anterior, se borra */
        CookieUtil.clearCookie(response, "refresh_token", secure);

        return ResponseEntity.noContent().build();
    }

    // metodo para obtener la informacion del usuario logueado, usando el id_token de la cookie
    @GetMapping("/me")
    public ResponseEntity<UsuarioSesionDto> quienSoy(HttpServletRequest request) {
        String idTokenCookie = CookieUtil.leerCookie(request, "id_token");
        if (idTokenCookie == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Jwt idToken = idTokenDecoder.decode(idTokenCookie);
            String email = idToken.getClaimAsString("email");
            String nombre = idToken.getClaimAsString("name");
            return ResponseEntity.ok(new UsuarioSesionDto(email, nombre != null ? nombre : email));
        } catch (JwtException e) {
            return ResponseEntity.status(401).build();
        }
    }

    // metodo para cerrar la sesion, eliminando las cookies
    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = esSegura(request);
        CookieUtil.clearCookie(response, "access_token", secure);
        CookieUtil.clearCookie(response, "id_token", secure);
        CookieUtil.clearCookie(response, "refresh_token", secure); /* por si queda una de una sesión anterior */
        return ResponseEntity.noContent().build();
    }

    private boolean esSegura(HttpServletRequest request) {
        return cookiesSeguras || request.isSecure();
    }
}
