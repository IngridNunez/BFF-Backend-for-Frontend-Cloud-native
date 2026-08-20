package com.sanosysalvos.bff.service;

import com.sanosysalvos.bff.config.ServiciosProperties;

import org.springframework.http.HttpMethod;  // ← CORRECTO
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
            return ResponseEntity.status(503).body("Servicio no disponible".getBytes());
        }

        String rutaDestino = request.getRequestURI().substring("/api/v1".length()); /* saca el prefijo /api/v1 */
        String urlDestino = "http://" + nombreEnEureka + rutaDestino; /* arma la URL completa al microservicio */

        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod()); /*
                                                                          * convierte el método HTTP a tipo HttpMethod
                                                                          */

        ResponseEntity<byte[]> respuesta = restClient.method(httpMethod)
                .uri(urlDestino)
                .body(body) /* envía el body original */
                .retrieve()
                .toEntity(byte[].class); /* devuelve la respuesta como bytes */

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