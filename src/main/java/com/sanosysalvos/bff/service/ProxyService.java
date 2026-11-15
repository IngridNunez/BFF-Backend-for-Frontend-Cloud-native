package com.sanosysalvos.bff.service;

import com.sanosysalvos.bff.config.ServiciosProperties;
import com.sanosysalvos.bff.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final ServiciosProperties serviciosProperties; /* propiedades de configuración de los servicios */
    private final RestClient restClient; /* cliente REST (con timeouts, ver RestClientConfig) */

    /* secreto de servicio compartido con los microservicios (INTERNAL_API_TOKEN). Se manda junto con los headers de
     * identidad: sin él, un microservicio no puede distinguir el X-User-Email que armó el bff del que pondría alguien
     * que lo llame directo (ms-chat lo usa para no avisar por correo a un tercero). Vacío = no se manda. */
    @Value("${internal.api-token:}")
    private String tokenInterno = "";

    /* Único conjunto de headers del cliente que se reenvía a los microservicios. Todo lo demás se
     * descarta, en especial Cookie (trae id_token y refresh_token, que los microservicios no deben
     * ver), Authorization (se reconstruye desde la cookie), Host, Origin y los X-Forwarded-*.
     * Los headers de identidad (X-User-Id, X-User-Email) NO están acá a propósito: los pone el bff
     * más abajo desde el token ya validado, así un cliente no puede suplantar a otro usuario. */
    private static final Set<String> HEADERS_PERMITIDOS_HACIA_MS = Set.of(
            "content-type", "accept", "accept-language");

    /* headers "hop-by-hop" (RFC 9110): describen la conexión entre el microservicio
     * y el bff, no entre el bff y el navegador, así que un proxy no debe reenviarlos.
     * Content-Length también se saca porque Spring lo vuelve a calcular con el body real.
     * Si se copiaban, una respuesta "Transfer-Encoding: chunked" del microservicio salía
     * del bff con chunked + Content-Length a la vez; el ALB la descartaba (protección
     * contra HTTP desync) y el navegador recibía 503 (pasaba con /mascotas/presigned-url). */
    private static final Set<String> HEADERS_HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade",
            "proxy-authenticate", "proxy-authorization", "content-length");

    /* método principal: recibe la solicitud y la reenvía al ms correspondiente */
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, byte[] body) {

        String destino = resolverDestino(request.getRequestURI());
        if (destino == null) {
            /* ruta sin microservicio (/api/v1, /api/v1/ o un nombre que no está en servicios.rutas) */
            return respuestaError(404, "Ruta no encontrada", false);
        }

        /* ms-mascotas (y en general los MS) sirven bajo /api/v1 tambien, asi que NO se
         * saca el prefijo al reenviar; de paso se preservan los query params (filtros) */
        String urlDestino = "http://" + destino + request.getRequestURI();
        if (request.getQueryString() != null) {
            urlDestino += "?" + request.getQueryString();
        }

        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod()); /* convierte el método HTTP */
        HttpHeaders headers = construirHeadersHaciaElMicroservicio(request);

        /* .body(null) explota (NPE) en peticiones sin body como GET/DELETE; solo se agrega si existe */
        RestClient.RequestBodySpec requestSpec = restClient.method(httpMethod)
                /* URI.create: la ruta y el query ya vienen codificados desde el navegador
                 * (ej. contentType=image%2Fjpeg); con uri(String) RestClient los volvía a
                 * codificar (%2F → %252F) y el microservicio recibía el texto literal "image%2Fjpeg" */
                .uri(URI.create(urlDestino))
                .headers(h -> h.addAll(headers));

        ResponseEntity<byte[]> respuesta;
        try {
            respuesta = (body != null ? requestSpec.body(body) : requestSpec)
                    .retrieve() // ejecuta la llamada http al microservicio destino
                    /* por defecto retrieve() lanza excepcion en 4xx/5xx; se desactiva para que el
                     * bff reenvie tal cual el error del microservicio, en vez de romper con un 500
                     * propio que además cae en /error (no publico) y confunde con un 401 */
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .toEntity(byte[].class);
        } catch (ResourceAccessException e) {
            /* no se pudo conectar o se agotó el timeout: el servicio está caído o colgado. 503 (no 2xx):
             * antes se respondía 202 "en cola" aunque nada se encolaba y el body se perdía */
            log.warn("Microservicio '{}' no disponible ({})", destino, e.getClass().getSimpleName());
            return respuestaError(503, "Servicio no disponible, reintenta en unos segundos", true);
        } catch (RestClientException e) {
            log.warn("Respuesta inválida del microservicio '{}' ({})", destino, e.getClass().getSimpleName());
            return respuestaError(502, "Respuesta inválida del servicio", false);
        }

        /* se reenvían los headers del microservicio salvo los hop-by-hop (ver HEADERS_HOP_BY_HOP) */
        HttpHeaders headersRespuesta = new HttpHeaders();
        respuesta.getHeaders().forEach((nombre, valores) -> {
            if (!HEADERS_HOP_BY_HOP.contains(nombre.toLowerCase())) {
                headersRespuesta.put(nombre, valores);
            }
        });

        return new ResponseEntity<>(respuesta.getBody(), headersRespuesta, respuesta.getStatusCode());
    }

    /* /api/v1/[servicio]/... → host:puerto del servicio, o null si la ruta no corresponde a ninguno */
    private String resolverDestino(String uri) {
        String[] segmentos = uri.split("/");
        if (segmentos.length < 4 || segmentos[3].isBlank()) {
            return null;
        }
        Map<String, String> rutas = serviciosProperties.getRutas();
        return rutas == null ? null : rutas.get(segmentos[3]);
    }

    private HttpHeaders construirHeadersHaciaElMicroservicio(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();

        Enumeration<String> nombres = request.getHeaderNames();
        if (nombres != null) {
            Collections.list(nombres).stream()
                    .filter(nombre -> HEADERS_PERMITIDOS_HACIA_MS.contains(nombre.toLowerCase()))
                    .forEach(nombre -> headers.add(nombre, request.getHeader(nombre)));
        }

        /* el access token viaja en la cookie httpOnly, no en el header Authorization - pero
         * ms-mascotas, ms-alertas y ms-chat validan su propio JWT esperando ese header
         * (arquitectura de "cada MS valida su token"), asi que se reconstruye acá a partir de la cookie */
        String accessToken = CookieUtil.leerCookie(request, "access_token");
        if (accessToken != null) {
            headers.set("Authorization", "Bearer " + accessToken);
        }

        /* identidad validada por el bff (JwtFilter): el sub y el correo del id token */
        String userId = (String) request.getAttribute("X-User-Id");
        if (userId != null) {
            headers.set("X-User-Id", userId);
        }
        String userEmail = (String) request.getAttribute("X-User-Email");
        if (userEmail != null) {
            headers.set("X-User-Email", userEmail);
        }
        /* solo en peticiones con sesión validada, que son las que llevan identidad */
        if ((userId != null || userEmail != null) && tokenInterno != null && !tokenInterno.isBlank()) {
            headers.set("X-Internal-Token", tokenInterno.strip());
        }
        return headers;
    }

    private ResponseEntity<byte[]> respuestaError(int estado, String mensaje, boolean reintentable) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (reintentable) {
            headers.set(HttpHeaders.RETRY_AFTER, "2");
        }
        byte[] cuerpo = ("{\"mensaje\":\"" + mensaje + "\"}").getBytes(StandardCharsets.UTF_8);
        return new ResponseEntity<>(cuerpo, headers, estado);
    }
}
