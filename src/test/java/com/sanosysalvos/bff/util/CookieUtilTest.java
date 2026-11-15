package com.sanosysalvos.bff.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    private String setCookie(MockHttpServletResponse response) {
        return response.getHeader("Set-Cookie");
    }

    @Test
    void setCookie_armaLaCookieHttpOnlySameSiteLaxConLaDuracionIndicada() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtil.setCookie(response, "access_token", "valor", Duration.ofMinutes(5), false);

        assertThat(setCookie(response)).startsWith("access_token=valor;")
                .contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=300")
                .doesNotContain("Secure");
    }

    @Test
    void setCookie_conSecureAgregaElAtributo() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtil.setCookie(response, "id_token", "v", Duration.ofMinutes(1), true);

        assertThat(setCookie(response)).contains("Secure");
    }

    @Test
    void clearCookie_laVaciaYLaExpira() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtil.clearCookie(response, "access_token", true);

        assertThat(setCookie(response)).startsWith("access_token=;").contains("Max-Age=0", "HttpOnly", "Secure");
    }

    private MockHttpServletRequest conCookie(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("Cookie", header);
        }
        return request;
    }

    @Test
    void leerCookie_sinHeaderCookieDevuelveNull() {
        assertThat(CookieUtil.leerCookie(conCookie(null), "access_token")).isNull();
    }

    @Test
    void leerCookie_encuentraLaCookieEntreVarias() {
        assertThat(CookieUtil.leerCookie(conCookie("a=1; access_token=abc; b=2"), "access_token")).isEqualTo("abc");
    }

    @Test
    void leerCookie_noConfundeNombresQueSoloCoincidenPorElFinalOElPrincipio() {
        MockHttpServletRequest request = conCookie("xaccess_token=malo; access_token_2=malo; access_token=bueno");

        assertThat(CookieUtil.leerCookie(request, "access_token")).isEqualTo("bueno");
    }

    @Test
    void leerCookie_conservaUnValorQueContieneIgual() {
        assertThat(CookieUtil.leerCookie(conCookie("id_token=eyJ0eXAi==.abc=="), "id_token")).isEqualTo("eyJ0eXAi==.abc==");
    }

    @Test
    void leerCookie_unaCookieDeTercerosMalFormadaNoImpideLeerLasNuestras() {
        /* Google Identity pone g_state con llaves y comillas; el parser estricto de Tomcat descartaría todo el header */
        MockHttpServletRequest request = conCookie("g_state={\"i_l\":0,\"i_ll\":1}; basura-sin-igual; access_token=abc");

        assertThat(CookieUtil.leerCookie(request, "access_token")).isEqualTo("abc");
    }

    @Test
    void leerCookie_ignoraEspaciosAlrededor() {
        assertThat(CookieUtil.leerCookie(conCookie("  access_token =  abc  "), "access_token")).isEqualTo("abc");
    }

    @Test
    void leerCookie_cookieInexistenteDevuelveNull() {
        assertThat(CookieUtil.leerCookie(conCookie("a=1; b=2"), "access_token")).isNull();
    }
}
