package com.sanosysalvos.bff.service;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod; // ← CORRECTO
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.sanosysalvos.bff.config.ServiciosProperties;
import com.sanosysalvos.bff.util.CookieUtil;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private final ServiciosProperties serviciosProperties; /* propiedades de configuración de los servicios */
    private final RestClient restClient; /* cliente REST para hacer solicitudes HTTP */

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

        String[] segmentos = request.getRequestURI().split("/"); /* divide la URI en segmentos */
        String nombreServicio = segmentos[3]; /* extrae el microservicio: /api/v1/[aquí]/..podemos cambiar a dos segundos el tiempo de respuesta. */ 
        String nombreEnEureka = serviciosProperties.getRutas().get(nombreServicio); /* busca el nombre en Eureka */

        if (!estaDisponible(nombreEnEureka)) {
            return ResponseEntity
                    .status(202).header("Retry-After", "2").body("Solicitud en cola, reintenta en 2 segundos"
                            .getBytes()); /* si no está disponible, devuelve 202 con retry-after */
        }
        /* ms-mascotas (y en general los MS) sirven bajo /api/v1 tambien, asi que NO se
         * saca el prefijo al reenviar; de paso se preservan los query params (filtros) */
        String urlDestino = "http://" + nombreEnEureka + request.getRequestURI();
        if (request.getQueryString() != null) {
            urlDestino += "?" + request.getQueryString();
        }

        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod()); /* convierte el método HTTP */

        /* copiar headers de la petición original */
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.add(name, request.getHeader(name)));

        /* el access token ahora viaja en la cookie httpOnly, no en el header
         * Authorization - pero ms-mascotas y ms-alertas validan su propio JWT
         * esperando ese header (arquitectura de "cada MS valida su token"),
         * asi que hay que reconstruirlo acá a partir de la cookie */
        String accessToken = CookieUtil.leerCookie(request, "access_token");
        if (accessToken != null) {
            headers.set("Authorization", "Bearer " + accessToken);
        }

        /* agregar el sub extraido del JWT como header para los microservicios */
        String userId = (String) request.getAttribute("X-User-Id");
        if (userId != null) {
            headers.add("X-User-Id", userId);
        }
        /* agregar el correo extraido del id token como header (ms-mascotas guarda el correo del dueño) */
        String userEmail = (String) request.getAttribute("X-User-Email");
        if (userEmail != null) {
            headers.add("X-User-Email", userEmail);
        }

        /* .body(null) explota (NPE) en peticiones sin body como GET/DELETE; solo se agrega si existe */
        RestClient.RequestBodySpec requestSpec = restClient.method(httpMethod)
                /* URI.create: la ruta y el query ya vienen codificados desde el navegador
                 * (ej. contentType=image%2Fjpeg); con uri(String) RestClient los volvía a
                 * codificar (%2F → %252F) y el microservicio recibía el texto literal "image%2Fjpeg" */
                .uri(URI.create(urlDestino))
                .headers(h -> h.addAll(headers)); /* pasa todos los headers incluyendo Authorization */

        ResponseEntity<byte[]> respuesta = (body != null ? requestSpec.body(body) : requestSpec)
                .retrieve()// ejecuta la llamada http para obtener(get) la respuesta del microservicio destino
                /* por defecto retrieve() lanza excepcion en 4xx/5xx; se desactiva para que el
                 * bff reenvie tal cual el error del microservicio, en vez de romper con un 500
                 * propio que además cae en /error (no publico) y confunde con un 401 */
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(byte[].class);

        /* se reenvían los headers del microservicio salvo los hop-by-hop (ver HEADERS_HOP_BY_HOP) */
        HttpHeaders headersRespuesta = new HttpHeaders();
        respuesta.getHeaders().forEach((nombre, valores) -> {
            if (!HEADERS_HOP_BY_HOP.contains(nombre.toLowerCase())) {
                headersRespuesta.put(nombre, valores);
            }
        });

        return new ResponseEntity<>(respuesta.getBody(), headersRespuesta, respuesta.getStatusCode());
    }

    /* verifica si el microservicio destino está disponible consultando su health */
    private boolean estaDisponible(String nombreEnEureka) {
        try {
            /* los microservicios exponen todo bajo /api/v1 (servlet-path), el health incluido */
            String url = "http://" + nombreEnEureka + "/api/v1/actuator/health";
            String respuesta = restClient.get() /* prepara la solicitud GET */
                    .uri(url) /* establece la URL destino */
                    .retrieve() /* ejecuta la solicitud */
                    .body(String.class); /* convierte la respuesta a String */
            return respuesta != null && respuesta.contains("UP");
        } catch (Exception e) {
            return false; /* si no responde, está caído */
        }
    }
}