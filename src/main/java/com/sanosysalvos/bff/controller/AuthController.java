package com.sanosysalvos.bff.controller;

import com.sanosysalvos.bff.dto.SesionRequestDto;
import com.sanosysalvos.bff.dto.UsuarioSesionDto;
import com.sanosysalvos.bff.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;

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
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final Duration DURACION_REFRESH_TOKEN = Duration.ofDays(30); /* default de Cognito para el refresh token */

    private final JwtDecoder jwtDecoder; /* valida access token (token_use=access) */
    private final JwtDecoder idTokenDecoder; /* valida id token (token_use=id) */

    @PostMapping("/session")
    public ResponseEntity<Void> iniciarSesion(@RequestBody SesionRequestDto dto, HttpServletRequest request, HttpServletResponse response) {
        Jwt accessToken;
        Jwt idToken;
        try {
            accessToken = jwtDecoder.decode(dto.getAccessToken());
            idToken = idTokenDecoder.decode(dto.getIdToken());
        } catch (JwtException e) {
            log.error("Token invalido al iniciar sesion", e);
            return ResponseEntity.status(401).build();
        }

        if (!accessToken.getSubject().equals(idToken.getSubject())) {
            log.error("Los subs de access token e id token no coinciden: {} vs {}", accessToken.getSubject(), idToken.getSubject());
            return ResponseEntity.status(401).build();
        }

        Duration duracionAccessToken = Duration.between(Instant.now(), accessToken.getExpiresAt());
        Duration duracionIdToken = Duration.between(Instant.now(), idToken.getExpiresAt());

        boolean secure = request.isSecure(); /* en dev local todo es http; en produccion (https) esto sera true */
        CookieUtil.setCookie(response, "access_token", dto.getAccessToken(), duracionAccessToken, secure);
        CookieUtil.setCookie(response, "id_token", dto.getIdToken(), duracionIdToken, secure);
        CookieUtil.setCookie(response, "refresh_token", dto.getRefreshToken(), DURACION_REFRESH_TOKEN, secure);

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
        boolean secure = request.isSecure();
        CookieUtil.clearCookie(response, "access_token", secure);
        CookieUtil.clearCookie(response, "id_token", secure);
        CookieUtil.clearCookie(response, "refresh_token", secure);
        return ResponseEntity.noContent().build();
    }
}
