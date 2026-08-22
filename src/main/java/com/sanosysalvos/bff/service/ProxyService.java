package com.sanosysalvos.bff.service;

import com.sanosysalvos.bff.config.ServiciosProperties;
import org.springframework.http.HttpHeaders;
import java.util.Collections;

import org.springframework.http.HttpMethod; // ← CORRECTO
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private final ServiciosProperties serviciosProperties; /* propiedades de configuración de los servicios */
    private final RestClient restClient; /* cliente REST para hacer solicitudes HTTP */

    /* método principal: recibe la solicitud y la reenvía al ms correspondiente */
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, byte[] body) {

        String[] segmentos = request.getRequestURI().split("/"); /* divide la URI en segmentos */
        String nombreServicio = segmentos[3]; /* extrae el microservicio: /api/v1/[aquí]/... */
        String nombreEnEureka = serviciosProperties.getRutas().get(nombreServicio); /* busca el nombre en Eureka */

        if (!estaDisponible(nombreEnEureka)) {
            return ResponseEntity
                    .status(202).header("Retry-After", "5").body("Solicitud en cola, reintenta en 5 segundos"
                            .getBytes()); /* si no está disponible, devuelve 202 con retry-after */
        }

        String rutaDestino = request.getRequestURI().substring("/api/v1".length()); /* saca el prefijo /api/v1 */
        String urlDestino = "http://" + nombreEnEureka + rutaDestino; /* arma la URL completa al microservicio */

        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod()); /* convierte el método HTTP */

        /* copiar headers de la petición original para que el JWT llegue a los MS */
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.add(name, request.getHeader(name)));

        ResponseEntity<byte[]> respuesta = restClient.method(httpMethod)
                .uri(urlDestino)
                .headers(h -> h.addAll(headers)) /* pasa todos los headers incluyendo Authorization */
                .body(body)
                .retrieve()// ejecuta la llamada http para obtener(get) la respuesta del microservicio destino
                .toEntity(byte[].class);

        return respuesta;
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