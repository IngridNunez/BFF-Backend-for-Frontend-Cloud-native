package com.sanosysalvos.bff.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteBodyFilterTest {

    private static final long MAXIMO = 1000;
    private final LimiteBodyFilter filtro = new LimiteBodyFilter(MAXIMO);

    private MockHttpServletResponse ejecutar(MockHttpServletRequest request, MockFilterChain cadena) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, cadena);
        return response;
    }

    private MockHttpServletRequest conBody(String metodo, int bytes) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/api/v1/contactos");
        request.setContent(new byte[bytes]);
        return request;
    }

    @Test
    void unBodyMasGrandeQueElMaximoSeRechazaConPayloadTooLarge() throws Exception {
        for (String metodo : new String[] { "POST", "PUT", "PATCH" }) {
            MockFilterChain cadena = new MockFilterChain();

            MockHttpServletResponse response = ejecutar(conBody(metodo, 1001), cadena);

            assertThat(response.getStatus()).as(metodo).isEqualTo(413);
            assertThat(cadena.getRequest()).as(metodo + " no debe leerse").isNull();
        }
    }

    @Test
    void unBodyDentroDelMaximoPasa() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse response = ejecutar(conBody("POST", 1000), cadena);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void unBodyPorPartesSinTamanoDeclaradoSeRechazaConLengthRequired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/contactos");
        request.addHeader("Transfer-Encoding", "chunked");
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse response = ejecutar(request, cadena);

        assertThat(response.getStatus()).isEqualTo(411);
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    void unaPeticionSinBodyPasa() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse response = ejecutar(new MockHttpServletRequest("POST", "/api/v1/auth/logout"), cadena);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void lasLecturasNoSeLimitan() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse response = ejecutar(conBody("GET", 5000), cadena);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(cadena.getRequest()).isNotNull();
    }
}
