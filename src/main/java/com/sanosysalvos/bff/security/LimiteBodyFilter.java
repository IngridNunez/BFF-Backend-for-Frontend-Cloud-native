package com.sanosysalvos.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/*
 * El bff recibe el body completo en memoria (@RequestBody byte[]) antes de reenviarlo, incluso en rutas
 * públicas como POST /contactos. Sin tope, unas pocas peticiones grandes agotan el heap del único punto
 * de entrada, y los @Size de los microservicios nunca llegan a aplicarse. Este filtro corta antes de leer
 * nada: 413 si el Content-Length declarado supera el máximo (bff.max-body-bytes, 1 MB por defecto) y 411
 * si el cliente manda el body "por partes" (chunked) sin declarar el tamaño, porque entonces no se puede
 * saber de antemano. Las fotos no pasan por acá: se suben directo a S3 con URL firmada.
 */
public class LimiteBodyFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_CON_BODY = Set.of("POST", "PUT", "PATCH");

    private final long maximoBytes;

    public LimiteBodyFilter(long maximoBytes) {
        this.maximoBytes = maximoBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (METODOS_CON_BODY.contains(request.getMethod())) {
            long largo = request.getContentLengthLong();
            if (largo > maximoBytes) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Body demasiado grande");
                return;
            }
            if (largo < 0 && request.getHeader("Transfer-Encoding") != null) {
                response.sendError(HttpServletResponse.SC_LENGTH_REQUIRED, "Falta Content-Length");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
