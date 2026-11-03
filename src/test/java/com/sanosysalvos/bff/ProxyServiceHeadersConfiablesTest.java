package com.sanosysalvos.bff;

import com.sanosysalvos.bff.config.ServiciosProperties;
import com.sanosysalvos.bff.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * X-User-Id y X-User-Email son los headers en los que los microservicios confian para saber
 * quien es el usuario. Solo el bff puede ponerlos (a partir del token ya validado): si el
 * cliente manda los suyos, no deben llegar al microservicio. Si llegaran duplicados, un
 * @RequestHeader toma el primero, que seria el del atacante (suplantacion de usuario).
 */
@ExtendWith(MockitoExtension.class)
class ProxyServiceHeadersConfiablesTest {

    @Mock
    private ServiciosProperties serviciosProperties;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private ProxyService proxyService;

    private RestClient.RequestBodySpec requestBodySpec;

    @BeforeEach
    void configurarRestClient() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("mascotas", "ms-mascotas"));

        /* health check del servicio destino */
        RestClient.RequestHeadersUriSpec healthUri = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec healthHeaders = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec healthResponse = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(healthUri);
        when(healthUri.uri(anyString())).thenReturn(healthHeaders);
        when(healthHeaders.retrieve()).thenReturn(healthResponse);
        when(healthResponse.body(String.class)).thenReturn("{\"status\":\"UP\"}");

        /* la peticion real reenviada */
        RestClient.RequestBodyUriSpec bodyUri = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.method(any())).thenReturn(bodyUri);
        when(bodyUri.uri(any(URI.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(response);
        when(response.onStatus(any(), any())).thenReturn(response);
        when(response.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(new byte[0]));
    }

    private HttpServletRequest peticionConHeadersDelCliente(String subReal, String emailReal) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/mascotas/123");
        when(request.getMethod()).thenReturn("PATCH");
        /* el atacante manda los suyos, con distintas mayusculas */
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(
                List.of("X-User-Id", "x-user-email", "Content-Type")));
        /* lenient: con el arreglo el bff ya no lee estos headers del cliente */
        lenient().when(request.getHeader("X-User-Id")).thenReturn("sub-de-la-victima");
        lenient().when(request.getHeader("x-user-email")).thenReturn("victima@example.com");
        when(request.getHeader("Content-Type")).thenReturn("application/json");
        /* lo que de verdad valido el bff */
        when(request.getAttribute("X-User-Id")).thenReturn(subReal);
        when(request.getAttribute("X-User-Email")).thenReturn(emailReal);
        return request;
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders headersReenviados() {
        ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(captor.capture());
        HttpHeaders destino = new HttpHeaders();
        captor.getValue().accept(destino);
        return destino;
    }

    @Test
    void usuarioAutenticado_alMicroservicioSoloLlegaSuIdentidadValidada() {
        proxyService.proxy(peticionConHeadersDelCliente("sub-real", "real@example.com"), null);

        HttpHeaders enviados = headersReenviados();
        assertThat(enviados.get("X-User-Id")).containsExactly("sub-real");
        assertThat(enviados.get("X-User-Email")).containsExactly("real@example.com");
        assertThat(enviados.getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void peticionSinSesion_losHeadersDeIdentidadDelClienteNoSeReenvian() {
        proxyService.proxy(peticionConHeadersDelCliente(null, null), null);

        HttpHeaders enviados = headersReenviados();
        assertThat(enviados.containsHeader("X-User-Id")).isFalse();
        assertThat(enviados.containsHeader("X-User-Email")).isFalse();
    }
}
