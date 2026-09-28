package com.sanosysalvos.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/*
 * Con cookies de sesion vuelve a existir riesgo de CSRF (antes no aplicaba
 * porque el token viajaba en un header, no en una cookie que el navegador
 * manda solo). En vez del mecanismo completo de token CSRF de Spring, se
 * exige un header custom en peticiones que cambian datos: un ataque CSRF
 * cross-site (formulario o fetch simple desde otro origen) no puede agregar
 * headers custom sin gatillar un preflight de CORS, que nuestra config de
 * origenes permitidos ya bloquea.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_PROTEGIDOS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String HEADER_ESPERADO = "X-Requested-With";
    private static final String VALOR_ESPERADO = "web";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (METODOS_PROTEGIDOS.contains(request.getMethod())
                && !VALOR_ESPERADO.equals(request.getHeader(HEADER_ESPERADO))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Falta header anti-CSRF");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
