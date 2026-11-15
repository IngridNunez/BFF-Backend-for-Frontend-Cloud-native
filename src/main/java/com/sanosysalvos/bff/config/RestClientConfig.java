package com.sanosysalvos.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    /* rest client es para hacer las llamadas http y como bean para inyectarlas donde necesitemos hacer esas llamadas.
     *
     * Con timeouts: sin ellos, un microservicio colgado deja bloqueados los hilos del bff (único punto
     * de entrada) hasta agotarlos. Si se agota alguno, ProxyService responde 503. Se configuran con
     * bff.timeout.conexion-ms (2 s) y bff.timeout.lectura-ms (10 s).
     *
     * Se usa el cliente HTTP del JDK (el mismo que RestClient elegía solo) porque soporta PATCH, que
     * HttpURLConnection no. HTTP/1.1 explícito: evita el intento de "upgrade a h2c" en HTTP plano. */
    @Bean
    public RestClient restClient(
            @Value("${bff.timeout.conexion-ms:2000}") long conexionMs,
            @Value("${bff.timeout.lectura-ms:10000}") long lecturaMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(conexionMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(Duration.ofMillis(lecturaMs));
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
