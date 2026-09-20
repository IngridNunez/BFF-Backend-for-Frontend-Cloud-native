package com.sanosysalvos.bff.service;

import java.util.Collections;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod; // ← CORRECTO
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.sanosysalvos.bff.config.ServiciosProperties;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private final ServiciosProperties serviciosProperties; /* propiedades de configuración de los servicios */
    private final RestClient restClient; /* cliente REST para hacer solicitudes HTTP */

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
        String rutaDestino = request.getRequestURI().substring("/api/v1".length());
        String queryString = request.getQueryString();
        String urlDestino = "http://" + nombreEnEureka + rutaDestino
                + (queryString != null ? "?" + queryString : ""); /* arma la URL completa al microservicio, con query params si los hay */

        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod()); /* convierte el método HTTP */

        /* copiar headers de la petición original para que el JWT llegue a los MS */
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.add(name, request.getHeader(name)));
        /* agregar el sub extraido del JWT como header para los microservicios */
        String userId = (String) request.getAttribute("X-User-Id");
        if (userId != null) {
            headers.add("X-User-Id", userId);
        }
        // ###################################
        /* agregar el correo extraido del id token como header (ms-mascotas guarda el correo del dueño) */
        String userEmail = (String) request.getAttribute("X-User-Email");
        if (userEmail != null) {
            headers.add("X-User-Email", userEmail);
        }
        // ###################################

        RestClient.RequestBodySpec requestSpec = restClient.method(httpMethod)
                .uri(urlDestino)
                .headers(h -> h.addAll(headers)); /* pasa todos los headers incluyendo Authorization */

        /* GET/DELETE no traen body — pasar null a .body() explota con NPE
         * (intenta leer body.getClass()), así que solo se llama si hay algo */
        RestClient.ResponseSpec responseSpec = (body != null ? requestSpec.body(body) : requestSpec)
                .retrieve()
                /* sin esto, un 4xx/5xx del microservicio hace que RestClient
                 * lance una excepción acá y el bff termine devolviendo un 500
                 * genérico (y, por el forward interno a /error, hasta un 401)
                 * en vez de reenviar el error real tal cual */
                .onStatus(status -> true, (req, res) -> { });

        return responseSpec.toEntity(byte[].class);
    }

    /* verifica si el microservicio destino está disponible consultando su health */
    private boolean estaDisponible(String nombreEnEureka) {
        try {
            String url = "http://" + nombreEnEureka + "/actuator/health";
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