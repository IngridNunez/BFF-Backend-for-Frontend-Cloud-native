package com.sanosysalvos.bff;

import com.sanosysalvos.bff.config.ServiciosProperties;
import com.sanosysalvos.bff.service.ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

    @Mock
    private ServiciosProperties serviciosProperties;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private ProxyService proxyService;

    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void configurarRutas() {
        lenient().when(serviciosProperties.getRutas())
                .thenReturn(Map.of("mascotas", "ms-mascotas:8082", "alertas", "ms-alertas:8083"));
    }

    /* arma la cadena de RestClient y hace que la llamada devuelva "respuesta" */
    private void elMicroserviceResponde(ResponseEntity<byte[]> respuesta) {
        prepararCadena();
        when(responseSpec.toEntity(byte[].class)).thenReturn(respuesta);
    }

    /* ... o que falle con la excepcion indicada */
    private void elMicroserviceFalla(RuntimeException error) {
        prepararCadena();
        when(responseSpec.toEntity(byte[].class)).thenThrow(error);
    }

    private void prepararCadena() {
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.method(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(URI.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.body(any(byte[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    private MockHttpServletRequest peticion(String metodo, String uri) {
        return new MockHttpServletRequest(metodo, uri);
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders headersReenviados() {
        ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(requestBodySpec).headers(captor.capture());
        HttpHeaders destino = new HttpHeaders();
        captor.getValue().accept(destino);
        return destino;
    }

    // ---------- reenvío básico ----------

    @Test
    void reenviaLaSolicitudAlPuertoDelServicioYDevuelveLaRespuesta() {
        elMicroserviceResponde(ResponseEntity.ok("hola".getBytes()));

        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", "/api/v1/mascotas"), "body".getBytes());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isEqualTo("hola".getBytes());
        verify(requestBodyUriSpec).uri(URI.create("http://ms-mascotas:8082/api/v1/mascotas"));
        verify(requestBodySpec).body("body".getBytes());
    }

    @Test
    void noHaceUnSondeoDeSaludAntesDeCadaLlamada() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));

        proxyService.proxy(peticion("GET", "/api/v1/mascotas"), null);

        /* antes se hacía un GET a /actuator/health del destino por cada petición (el doble de latencia y carga) */
        verify(restClient, never()).get();
    }

    @Test
    void conBodyNuloNoLlamaABody() {
        elMicroserviceResponde(ResponseEntity.ok("mascotas".getBytes()));

        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", "/api/v1/mascotas"), null);

        assertThat(respuesta.getBody()).isEqualTo("mascotas".getBytes());
        verify(requestBodySpec, never()).body(any(byte[].class));
    }

    @Test
    void incluyeElQueryStringEnLaUrlDestino() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("GET", "/api/v1/alertas/zona");
        request.setQueryString("lat=1&lng=2&radioKm=3");

        proxyService.proxy(request, null);

        verify(requestBodyUriSpec).uri(URI.create("http://ms-alertas:8083/api/v1/alertas/zona?lat=1&lng=2&radioKm=3"));
    }

    @Test
    void noVuelveACodificarElQueryYaCodificado() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("GET", "/api/v1/mascotas/presigned-url");
        request.setQueryString("contentType=image%2Fjpeg");

        proxyService.proxy(request, null);

        verify(requestBodyUriSpec).uri(URI.create("http://ms-mascotas:8082/api/v1/mascotas/presigned-url?contentType=image%2Fjpeg"));
    }

    @Test
    void construyeLaUrlConRutaAnidada() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));

        proxyService.proxy(peticion("GET", "/api/v1/mascotas/123/estado"), null);

        verify(requestBodyUriSpec).uri(URI.create("http://ms-mascotas:8082/api/v1/mascotas/123/estado"));
    }

    @Test
    void usaElMetodoHttpDeLaPeticion() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));

        proxyService.proxy(peticion("PATCH", "/api/v1/mascotas/123"), "{}".getBytes());

        verify(restClient).method(HttpMethod.PATCH);
    }

    // ---------- respuesta ----------

    @Test
    void quitaLosHeadersHopByHopDeLaRespuestaYMantieneLosDemas() {
        HttpHeaders delMicroservicio = new HttpHeaders();
        delMicroservicio.add("Transfer-Encoding", "chunked");
        delMicroservicio.add("Connection", "keep-alive");
        delMicroservicio.add("Content-Length", "4");
        delMicroservicio.add("Content-Type", "application/json");
        delMicroservicio.add("X-Otro", "valor");
        elMicroserviceResponde(new ResponseEntity<>("{}".getBytes(), delMicroservicio, 200));

        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", "/api/v1/mascotas"), null);

        assertThat(respuesta.getHeaders().containsHeader("Transfer-Encoding")).isFalse();
        assertThat(respuesta.getHeaders().containsHeader("Connection")).isFalse();
        assertThat(respuesta.getHeaders().containsHeader("Content-Length")).isFalse();
        assertThat(respuesta.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(respuesta.getHeaders().getFirst("X-Otro")).isEqualTo("valor");
    }

    @Test
    void reenviaTalCualElErrorDelMicroservicio() {
        elMicroserviceResponde(ResponseEntity.status(404).body("{\"mensaje\":\"no existe\"}".getBytes()));

        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", "/api/v1/mascotas/x"), null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(new String(respuesta.getBody())).contains("no existe");
    }

    // ---------- headers hacia el microservicio (lista permitida) ----------

    @Test
    void soloSeReenvianLosHeadersPermitidosYNuncaLasCookies() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("POST", "/api/v1/mascotas");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept", "application/json");
        request.addHeader("Accept-Language", "es-CL");
        /* nada de esto debe llegar a los microservicios */
        request.addHeader("Cookie", "access_token=a; id_token=i; refresh_token=r");
        request.addHeader("Authorization", "Bearer token-que-manda-el-cliente");
        request.addHeader("Origin", "https://otro-sitio.example");
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        request.addHeader("X-Requested-With", "web");
        request.addHeader("User-Agent", "curl");

        proxyService.proxy(request, "{}".getBytes());

        HttpHeaders enviados = headersReenviados();
        assertThat(enviados.getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(enviados.getFirst("Accept")).isEqualTo("application/json");
        assertThat(enviados.getFirst("Accept-Language")).isEqualTo("es-CL");
        assertThat(enviados.headerNames()).doesNotContain("Cookie", "Origin", "X-Forwarded-For", "X-Requested-With", "User-Agent");
        /* el Authorization que manda el cliente no pasa: solo se arma uno desde la cookie access_token */
        assertThat(enviados.get("Authorization")).containsExactly("Bearer a");
    }

    @Test
    void elAuthorizationSeReconstruyeDesdeLaCookieDeAccessToken() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("GET", "/api/v1/mascotas/mis-mascotas");
        request.addHeader("Cookie", "id_token=i; access_token=mi-access-token; refresh_token=r");
        request.addHeader("Authorization", "Bearer otro-token");

        proxyService.proxy(request, null);

        HttpHeaders enviados = headersReenviados();
        assertThat(enviados.get("Authorization")).containsExactly("Bearer mi-access-token");
        assertThat(enviados.containsHeader("Cookie")).isFalse();
    }

    // ---------- identidad: solo la que valida el bff ----------

    @Test
    void usuarioAutenticadoAlMicroservicioSoloLlegaSuIdentidadValidada() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("PATCH", "/api/v1/mascotas/123");
        /* el atacante manda los suyos, con distintas mayúsculas */
        request.addHeader("X-User-Id", "sub-de-la-victima");
        request.addHeader("x-user-email", "victima@example.com");
        /* lo que de verdad validó el bff */
        request.setAttribute("X-User-Id", "sub-real");
        request.setAttribute("X-User-Email", "real@example.com");

        proxyService.proxy(request, "{}".getBytes());

        HttpHeaders enviados = headersReenviados();
        assertThat(enviados.get("X-User-Id")).containsExactly("sub-real");
        assertThat(enviados.get("X-User-Email")).containsExactly("real@example.com");
    }

    @Test
    void peticionSinSesionLosHeadersDeIdentidadDelClienteNoSeReenvian() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("GET", "/api/v1/mascotas");
        request.addHeader("X-User-Id", "sub-de-la-victima");
        request.addHeader("X-User-Email", "victima@example.com");

        proxyService.proxy(request, null);

        HttpHeaders enviados = headersReenviados();
        assertThat(enviados.containsHeader("X-User-Id")).isFalse();
        assertThat(enviados.containsHeader("X-User-Email")).isFalse();
    }

    // ---------- rutas mal formadas ----------

    @Test
    void unaRutaSinServicioDevuelve404SinLlamarANadie() {
        for (String uri : new String[] { "/api/v1", "/api/v1/", "/api/v1//mascotas" }) {
            ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", uri), null);

            assertThat(respuesta.getStatusCode().value()).as(uri).isEqualTo(404);
        }
        verifyNoInteractions(restClient);
    }

    @Test
    void unServicioDesconocidoDevuelve404EnVezDeConsultarHttpNull() {
        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", "/api/v1/foo/x"), null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(new String(respuesta.getBody())).contains("Ruta no encontrada");
        verifyNoInteractions(restClient);
    }

    // ---------- microservicio caído o colgado ----------

    @Test
    void siElMicroservicioNoResponde_devuelve503ConRetryAfterYNoUn202() {
        elMicroserviceFalla(new ResourceAccessException("Connection refused"));

        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("POST", "/api/v1/mascotas"), "{}".getBytes());

        /* antes: 202 "Solicitud en cola" aunque nada se encolaba y el reporte se perdía */
        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(respuesta.getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(respuesta.getHeaders().getContentType().toString()).startsWith("application/json");
    }

    @Test
    void siSeAgotaElTimeout_devuelve503() {
        elMicroserviceFalla(new ResourceAccessException("Read timed out"));

        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", "/api/v1/mascotas"), null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void siLaRespuestaDelMicroservicioEsInvalida_devuelve502() {
        elMicroserviceFalla(new RestClientException("respuesta ilegible"));

        ResponseEntity<byte[]> respuesta = proxyService.proxy(peticion("GET", "/api/v1/mascotas"), null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(502);
    }

    // ---------- secreto de servicio ----------

    private static final String SECRETO = "secreto-de-servicio-de-prueba-de-al-menos-32-caracteres";

    @Test
    void laPeticionConSesionLlevaElSecretoDeServicioParaQueElMicroservicioConfieEnLaIdentidad() {
        org.springframework.test.util.ReflectionTestUtils.setField(proxyService, "tokenInterno", SECRETO);
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("POST", "/api/v1/mascotas/123");
        request.setAttribute("X-User-Id", "sub-real");
        request.setAttribute("X-User-Email", "real@example.com");

        proxyService.proxy(request, "{}".getBytes());

        assertThat(headersReenviados().get("X-Internal-Token")).containsExactly(SECRETO);
    }

    @Test
    void sinSesionNoSeEnviaElSecreto() {
        org.springframework.test.util.ReflectionTestUtils.setField(proxyService, "tokenInterno", SECRETO);
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));

        proxyService.proxy(peticion("GET", "/api/v1/mascotas"), null);

        assertThat(headersReenviados().containsHeader("X-Internal-Token")).isFalse();
    }

    @Test
    void elSecretoQueMandeElClienteNuncaSeReenvia() {
        elMicroserviceResponde(ResponseEntity.ok(new byte[0]));
        MockHttpServletRequest request = peticion("GET", "/api/v1/mascotas");
        request.addHeader("X-Internal-Token", "token-inventado-por-el-cliente");
        request.setAttribute("X-User-Id", "sub-real");

        proxyService.proxy(request, null);

        /* sin secreto configurado en el bff no se manda ninguno, y el del cliente tampoco pasa */
        assertThat(headersReenviados().containsHeader("X-Internal-Token")).isFalse();
    }
}
