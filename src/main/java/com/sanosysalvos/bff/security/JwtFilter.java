package com.sanosysalvos.bff.security;

import com.sanosysalvos.bff.util.CookieUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    private final JwtDecoder idTokenDecoder; /* decoder específico para el X-Id-Token (no exige token_use=access) */

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        /* si la ruta es pública (permitAll) y no llegó ningún access token, no hay nada que validar aquí */
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            log.info("Sin autenticacion (anonima o nula) para {} {} - cookie header crudo: {}",
                    request.getMethod(), request.getRequestURI(), request.getHeader("Cookie"));
            filterChain.doFilter(request, response);
            return;
        }

        /* extraer el id token de la cookie httpOnly (antes viajaba en el header X-Id-Token) */
        String idTokenString = CookieUtil.leerCookie(request, "id_token");
        if (idTokenString == null || idTokenString.isBlank()) {
            log.error("Id token no proporcionado. Cookie header crudo: {}", request.getHeader("Cookie"));
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Id token no proporcionado");
            return;
        }

        /* verificar que el refresh token existe y no está vacío (antes viajaba en X-Refresh-Token) */
        String refreshToken = CookieUtil.leerCookie(request, "refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            log.error("Refresh token no proporcionado. Cookie header crudo: {}", request.getHeader("Cookie"));
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh token no proporcionado");
            return;
        }

        try {
            /* validar el id token usando el decoder de Spring Security */
            Jwt idToken = idTokenDecoder.decode(idTokenString);

            /*
             * obtener el sub del access token ya validado por Spring Security , el sub es
             * el identificador único del usuario
             */
            Jwt accessToken = (Jwt) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            String accessSub = accessToken.getSubject();

            /* verificar que el sub coincida en ambos tokens */
            if (!accessSub.equals(idToken.getSubject())) {
                log.error("Los subs no coinciden: access={} id={}", accessSub, idToken.getSubject());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tokens no coinciden");
                return;
            }

            /* pasar el sub como atributo para los microservicios */
            request.setAttribute("X-User-Id", accessSub);

            /* pasar el correo del id token como atributo para los microservicios (ms-mascotas lo usa como dato de usuario) */
            String email = idToken.getClaimAsString("email");
            if (email != null) {
                request.setAttribute("X-User-Email", email);
            }

            /* todo válido, dejar pasar */
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Id token invalido", e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Id token inválido");
        }
    }
}
