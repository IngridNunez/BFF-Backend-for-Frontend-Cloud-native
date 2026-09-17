package com.sanosysalvos.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtFilter jwtFilter;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwtConSubject(String subject) {
        return Jwt.withTokenValue("token-" + subject)
                .header("alg", "none")
                .subject(subject)
                .claim("sub", subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private void autenticarComo(Jwt accessJwt) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(accessJwt, null));
    }

    @Test
    void doFilter_sinAutenticacionEnContexto_dejaPasarSinValidarTokens() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(response);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void doFilter_autenticacionAnonima_dejaPasarSinValidarTokens() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(response);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void doFilter_sinHeaderXIdToken_devuelve401YNoContinua() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        autenticarComo(jwtConSubject("usuario-1"));
        when(request.getHeader("X-Id-Token")).thenReturn(null);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Id token no proporcionado");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_headerXIdTokenSinPrefijoBearer_devuelve401() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        autenticarComo(jwtConSubject("usuario-1"));
        when(request.getHeader("X-Id-Token")).thenReturn("id-token-sin-bearer");

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Id token no proporcionado");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_sinRefreshToken_devuelve401() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        autenticarComo(jwtConSubject("usuario-1"));
        when(request.getHeader("X-Id-Token")).thenReturn("Bearer id-token");
        when(request.getHeader("X-Refresh-Token")).thenReturn(null);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh token no proporcionado");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_refreshTokenEnBlanco_devuelve401() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        autenticarComo(jwtConSubject("usuario-1"));
        when(request.getHeader("X-Id-Token")).thenReturn("Bearer id-token");
        when(request.getHeader("X-Refresh-Token")).thenReturn("   ");

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh token no proporcionado");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_idTokenInvalido_devuelve401() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        autenticarComo(jwtConSubject("usuario-1"));
        when(request.getHeader("X-Id-Token")).thenReturn("Bearer id-token");
        when(request.getHeader("X-Refresh-Token")).thenReturn("refresh-token");
        when(jwtDecoder.decode("id-token")).thenThrow(new RuntimeException("firma inválida"));

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Id token inválido");
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_subsNoCoinciden_devuelve401() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        when(request.getHeader("X-Id-Token")).thenReturn("Bearer id-token");
        when(request.getHeader("X-Refresh-Token")).thenReturn("refresh-token");
        when(jwtDecoder.decode("id-token")).thenReturn(jwtConSubject("usuario-id-token"));
        autenticarComo(jwtConSubject("usuario-access-token"));

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tokens no coinciden");
        verifyNoInteractions(filterChain);
        verify(request, never()).setAttribute(any(), any());
    }

    @Test
    void doFilter_tokensValidosYSubsCoinciden_dejaPasarYPropagaXUserId() throws Exception {
        jwtFilter = new JwtFilter(jwtDecoder);
        when(request.getHeader("X-Id-Token")).thenReturn("Bearer id-token");
        when(request.getHeader("X-Refresh-Token")).thenReturn("refresh-token");
        when(jwtDecoder.decode("id-token")).thenReturn(jwtConSubject("usuario-1"));
        autenticarComo(jwtConSubject("usuario-1"));

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute("X-User-Id", "usuario-1");
        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(response);
    }
}
