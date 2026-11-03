package com.sanosysalvos.bff.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfHeaderFilterTest {

    private final CsrfHeaderFilter filtro = new CsrfHeaderFilter();

    private MockHttpServletResponse ejecutar(String metodo, String valorHeader, MockFilterChain cadena) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/api/v1/mascotas");
        if (valorHeader != null) {
            request.addHeader("X-Requested-With", valorHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, cadena);
        return response;
    }

    @Test
    void losMetodosQueCambianDatosSinElHeaderSeRechazanConForbidden() throws Exception {
        for (String metodo : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
            MockFilterChain cadena = new MockFilterChain();

            MockHttpServletResponse response = ejecutar(metodo, null, cadena);

            assertThat(response.getStatus()).as(metodo).isEqualTo(403);
            assertThat(cadena.getRequest()).as(metodo + " no debe seguir la cadena").isNull();
        }
    }

    @Test
    void conElHeaderCorrectoDejaPasar() throws Exception {
        for (String metodo : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
            MockFilterChain cadena = new MockFilterChain();

            MockHttpServletResponse response = ejecutar(metodo, "web", cadena);

            assertThat(response.getStatus()).as(metodo).isEqualTo(200);
            assertThat(cadena.getRequest()).as(metodo).isNotNull();
        }
    }

    @Test
    void unValorDistintoDeWebTambienSeRechaza() throws Exception {
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse response = ejecutar("POST", "XMLHttpRequest", cadena);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    void lasLecturasNoNecesitanElHeader() throws Exception {
        for (String metodo : new String[] { "GET", "HEAD", "OPTIONS" }) {
            MockFilterChain cadena = new MockFilterChain();

            MockHttpServletResponse response = ejecutar(metodo, null, cadena);

            assertThat(response.getStatus()).as(metodo).isEqualTo(200);
            assertThat(cadena.getRequest()).as(metodo).isNotNull();
        }
    }
}
