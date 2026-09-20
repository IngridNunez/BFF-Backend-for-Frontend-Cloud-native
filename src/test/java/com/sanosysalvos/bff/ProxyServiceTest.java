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

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

    @Mock
    private ServiciosProperties serviciosProperties;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private ProxyService proxyService;

    private RestClient.RequestHeadersUriSpec healthUriSpec;
    private RestClient.RequestHeadersSpec healthHeadersSpec;
    private RestClient.ResponseSpec healthResponseSpec;

    @BeforeEach
    void setUpHealthCheck() {
        healthUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        healthHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        healthResponseSpec = mock(RestClient.ResponseSpec.class);
    }

    private void mockServicioDisponible(boolean disponible) {
        when(restClient.get()).thenReturn(healthUriSpec);
        when(healthUriSpec.uri(anyString())).thenReturn(healthHeadersSpec);
        when(healthHeadersSpec.retrieve()).thenReturn(healthResponseSpec);
        if (disponible) {
            when(healthResponseSpec.body(String.class)).thenReturn("{\"status\":\"UP\"}");
        } else {
            when(healthResponseSpec.body(String.class)).thenThrow(new RuntimeException("no responde"));
        }
    }

    private HttpServletRequest mockRequest(String uri, String metodo, String userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn(metodo);
        when(request.getHeaderNames())
                .thenReturn(Collections.enumeration(java.util.List.of("Authorization")));
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        when(request.getAttribute("X-User-Id")).thenReturn(userId);
        return request;
    }

    private HttpServletRequest mockRequestConQuery(String uri, String query) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(query);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(java.util.List.of()));
        return request;
    }

    private HttpServletRequest mockRequestSoloUri(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void proxy_cuandoServicioDisponible_reenviaSolicitudYDevuelveRespuesta() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("mascotas", "ms-mascotas"));
        mockServicioDisponible(true);

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        ResponseEntity<byte[]> respuestaEsperada = ResponseEntity.ok("hola".getBytes());

        when(restClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(byte[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenReturn(respuestaEsperada);

        HttpServletRequest request = mockRequest("/api/v1/mascotas", "GET", null);

        ResponseEntity<byte[]> respuesta = proxyService.proxy(request, "body".getBytes());

        assertThat(respuesta).isSameAs(respuestaEsperada);
        verify(requestBodyUriSpec).uri("http://ms-mascotas/mascotas");
    }

    @Test
    void proxy_cuandoServicioNoDisponible_devuelve202ConRetryAfter() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("mascotas", "ms-mascotas"));
        mockServicioDisponible(false);

        HttpServletRequest request = mockRequestSoloUri("/api/v1/mascotas");

        ResponseEntity<byte[]> respuesta = proxyService.proxy(request, null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(202);
        assertThat(respuesta.getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(new String(respuesta.getBody())).isEqualTo("Solicitud en cola, reintenta en 2 segundos");
    }

    @Test
    void proxy_agregaHeaderXUserId_cuandoEstaPresenteEnLaRequest() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("mascotas", "ms-mascotas"));
        mockServicioDisponible(true);

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(byte[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(new byte[0]));

        HttpServletRequest request = mockRequest("/api/v1/mascotas", "GET", "user-123");

        proxyService.proxy(request, new byte[0]);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(captor.capture());

        HttpHeaders headers = new HttpHeaders();
        captor.getValue().accept(headers);

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-123");
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer access-token");
    }

    @Test
    void proxy_noAgregaHeaderXUserId_cuandoNoEstaPresenteEnLaRequest() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("mascotas", "ms-mascotas"));
        mockServicioDisponible(true);

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(byte[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(new byte[0]));

        HttpServletRequest request = mockRequest("/api/v1/mascotas", "GET", null);

        proxyService.proxy(request, new byte[0]);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(captor.capture());

        HttpHeaders headers = new HttpHeaders();
        captor.getValue().accept(headers);

        assertThat(headers.get("X-User-Id")).isNull();
    }

    @Test
    void proxy_conBodyNulo_noLanzaNpeYNoLlamaBody() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("mascotas", "ms-mascotas"));
        mockServicioDisponible(true);

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        ResponseEntity<byte[]> respuestaEsperada = ResponseEntity.ok("mascotas".getBytes());

        when(restClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenReturn(respuestaEsperada);

        HttpServletRequest request = mockRequest("/api/v1/mascotas", "GET", null);

        ResponseEntity<byte[]> respuesta = proxyService.proxy(request, null);

        assertThat(respuesta).isSameAs(respuestaEsperada);
        verify(requestBodySpec, org.mockito.Mockito.never()).body(any(byte[].class));
    }

    @Test
    void proxy_conQueryString_laIncluyeEnLaUrlDestino() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("alertas", "ms-alertas"));
        mockServicioDisponible(true);

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(new byte[0]));

        HttpServletRequest request = mockRequestConQuery("/api/v1/alertas/zona", "lat=1&lng=2&radioKm=3");

        proxyService.proxy(request, null);

        verify(requestBodyUriSpec).uri("http://ms-alertas/alertas/zona?lat=1&lng=2&radioKm=3");
    }

    @Test
    void proxy_construyeUrlDestinoConRutaAnidada() {
        when(serviciosProperties.getRutas()).thenReturn(Map.of("mascotas", "ms-mascotas"));
        mockServicioDisponible(true);

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(byte[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(byte[].class)).thenReturn(ResponseEntity.ok(new byte[0]));

        HttpServletRequest request = mockRequest("/api/v1/mascotas/123", "GET", null);

        proxyService.proxy(request, new byte[0]);

        verify(requestBodyUriSpec).uri("http://ms-mascotas/mascotas/123");
    }
}
