package com.sanosysalvos.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder; /* decoder de Spring Security, ya tiene las claves de Cognito */

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        /* extraer el id token (sub) */
        String idTokenHeader = request.getHeader("X-Id-Token");
        if (idTokenHeader == null || !idTokenHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Id token no proporcionado");
            return;
        }
        String idTokenString = idTokenHeader.substring(7);

        /* verificar que el refresh token existe y no está vacío */
        String refreshToken = request.getHeader("X-Refresh-Token");
        if (refreshToken == null || refreshToken.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh token no proporcionado");
            return;
        }

        try {
            /* validar el id token usando el decoder de Spring Security */
            Jwt idToken = jwtDecoder.decode(idTokenString);

            /*
             * obtener el sub del access token ya validado por Spring Security , el sub es
             * el identificador único del usuario
             */
            Jwt accessToken = (Jwt) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal();
            String accessSub = accessToken.getSubject();

            /* verificar que el sub coincida en ambos tokens */
            if (!accessSub.equals(idToken.getSubject())) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tokens no coinciden");
                return;
            }

            /* pasar el sub como atributo para los microservicios */
            request.setAttribute("X-User-Id", accessSub);

            /* todo válido, dejar pasar */
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Id token inválido");
        }
    }
}
