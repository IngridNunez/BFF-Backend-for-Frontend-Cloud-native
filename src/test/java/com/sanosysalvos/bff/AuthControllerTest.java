package com.sanosysalvos.bff;

import com.sanosysalvos.bff.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String SESION = "/api/v1/auth/session";
    private static final String CUERPO_VALIDO =
            "{\"accessToken\":\"acc\",\"idToken\":\"idt\",\"refreshToken\":\"refresh-que-se-ignora\"}";

    @Mock
    private JwtDecoder accessDecoder;

    @Mock
    private JwtDecoder idDecoder;

    private MockMvc mvc(boolean cookiesSeguras) {
        return MockMvcBuilders.standaloneSetup(new AuthController(accessDecoder, idDecoder, cookiesSeguras)).build();
    }

    private Jwt jwt(String sub, String email, String nombre) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
        if (email != null) {
            b.claim("email", email);
        }
        if (nombre != null) {
            b.claim("name", nombre);
        }
        return b.build();
    }

    private void tokensValidos() {
        when(accessDecoder.decode("acc")).thenReturn(jwt("usuario-1", null, null));
        when(idDecoder.decode("idt")).thenReturn(jwt("usuario-1", "persona@example.com", "Persona"));
    }

    private List<String> cookies(MvcResult resultado) {
        return resultado.getResponse().getHeaders("Set-Cookie");
    }

    private String cookie(List<String> todas, String nombre) {
        return todas.stream().filter(c -> c.startsWith(nombre + "=")).findFirst().orElse(null);
    }

    // ---------- iniciar sesión ----------

    @Test
    void sesionValida_emiteAccessEIdTokenComoCookiesHttpOnly() throws Exception {
        tokensValidos();

        MvcResult res = mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNoContent()).andReturn();

        for (String nombre : List.of("access_token", "id_token")) {
            String c = cookie(cookies(res), nombre);
            assertThat(c).as(nombre).isNotNull().contains("HttpOnly", "SameSite=Lax", "Path=/");
            assertThat(c).doesNotContain("Max-Age=0");
        }
        assertThat(cookie(cookies(res), "access_token")).startsWith("access_token=acc;");
    }

    @Test
    void sesionValida_noGuardaElRefreshTokenYLimpiaUnoDeUnaSesionAnterior() throws Exception {
        tokensValidos();

        MvcResult res = mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isNoContent()).andReturn();

        String refresh = cookie(cookies(res), "refresh_token");
        assertThat(refresh).as("solo se borra, no se emite").startsWith("refresh_token=;").contains("Max-Age=0");
        assertThat(String.join("|", cookies(res))).doesNotContain("refresh-que-se-ignora");
    }

    @Test
    void enLocalPorHttp_lasCookiesNoLlevanSecure() throws Exception {
        tokensValidos();

        MvcResult res = mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andReturn();

        assertThat(cookie(cookies(res), "access_token")).doesNotContain("Secure");
    }

    @Test
    void conBffCookiesSecure_lasCookiesSalenConSecureAunqueElBffVeaHttp() throws Exception {
        /* detrás de API Gateway/ALB el TLS ya se terminó: request.isSecure() es false aunque el navegador use https */
        tokensValidos();

        MvcResult res = mvc(true).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andReturn();

        assertThat(cookie(cookies(res), "access_token")).contains("Secure");
        assertThat(cookie(cookies(res), "id_token")).contains("Secure");
    }

    @Test
    void conConexionHttps_lasCookiesSalenConSecure() throws Exception {
        tokensValidos();

        MvcResult res = mvc(false).perform(post(SESION).secure(true)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO)).andReturn();

        assertThat(cookie(cookies(res), "access_token")).contains("Secure");
    }

    @Test
    void accessTokenInvalido_devuelve401SinEmitirCookies() throws Exception {
        when(accessDecoder.decode("acc")).thenThrow(new BadJwtException("firma inválida"));

        MvcResult res = mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(cookies(res)).isEmpty();
    }

    @Test
    void idTokenInvalido_devuelve401SinEmitirCookies() throws Exception {
        when(accessDecoder.decode("acc")).thenReturn(jwt("usuario-1", null, null));
        when(idDecoder.decode("idt")).thenThrow(new BadJwtException("audiencia incorrecta"));

        MvcResult res = mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(cookies(res)).isEmpty();
    }

    @Test
    void tokensDeUsuariosDistintos_devuelve401() throws Exception {
        when(accessDecoder.decode("acc")).thenReturn(jwt("usuario-1", null, null));
        when(idDecoder.decode("idt")).thenReturn(jwt("usuario-2", "otra@example.com", null));

        MvcResult res = mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(cookies(res)).isEmpty();
    }

    @Test
    void sinTokensOEnBlanco_devuelve400SinTocarLosDecoders() throws Exception {
        for (String cuerpo : List.of("{}", "{\"accessToken\":\"acc\"}", "{\"accessToken\":\"  \",\"idToken\":\"idt\"}")) {
            mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(accessDecoder, idDecoder);
    }

    @Test
    void tokenDemasiadoLargo_devuelve400() throws Exception {
        String cuerpo = "{\"accessToken\":\"" + "a".repeat(9000) + "\",\"idToken\":\"idt\"}";

        mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accessDecoder, idDecoder);
    }

    @Test
    void jsonMalformado_devuelve400() throws Exception {
        mvc(false).perform(post(SESION).contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accessDecoder, idDecoder);
    }

    // ---------- quién soy ----------

    @Test
    void me_conIdTokenValidoDevuelveCorreoYNombre() throws Exception {
        when(idDecoder.decode("idt")).thenReturn(jwt("usuario-1", "persona@example.com", "Persona"));

        mvc(false).perform(get("/api/v1/auth/me").header("Cookie", "otra=1; id_token=idt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("persona@example.com"))
                .andExpect(jsonPath("$.nombre").value("Persona"));
    }

    @Test
    void me_sinNombreUsaElCorreo() throws Exception {
        when(idDecoder.decode("idt")).thenReturn(jwt("usuario-1", "persona@example.com", null));

        mvc(false).perform(get("/api/v1/auth/me").header("Cookie", "id_token=idt"))
                .andExpect(jsonPath("$.nombre").value("persona@example.com"));
    }

    @Test
    void me_sinCookieDevuelve401() throws Exception {
        mvc(false).perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(idDecoder);
    }

    @Test
    void me_conIdTokenInvalidoDevuelve401() throws Exception {
        when(idDecoder.decode("idt")).thenThrow(new BadJwtException("expirado"));

        mvc(false).perform(get("/api/v1/auth/me").header("Cookie", "id_token=idt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- cerrar sesión ----------

    @Test
    void logout_borraLasTresCookies() throws Exception {
        MvcResult res = mvc(false).perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent()).andReturn();

        for (String nombre : List.of("access_token", "id_token", "refresh_token")) {
            assertThat(cookie(cookies(res), nombre)).as(nombre).startsWith(nombre + "=;").contains("Max-Age=0");
        }
    }
}
