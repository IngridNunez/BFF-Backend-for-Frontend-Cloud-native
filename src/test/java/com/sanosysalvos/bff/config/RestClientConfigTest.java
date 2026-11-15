package com.sanosysalvos.bff.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/* servidor HTTP real y local: se comprueba que los timeouts cortan y que PATCH sigue funcionando */
class RestClientConfigTest {

    private HttpServer servidor;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/lento", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            responder(exchange, "tarde");
        });
        servidor.createContext("/metodo", exchange -> responder(exchange, exchange.getRequestMethod()));
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange exchange, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String base() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Test
    void unServicioQueTardaMasQueElTimeoutDeLecturaCortaConResourceAccessException() {
        RestClient cliente = new RestClientConfig().restClient(1000, 300);

        /* sin timeout esto esperaría los 2 s del servidor (o para siempre si se cuelga) */
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> cliente.get().uri(base() + "/lento").retrieve().body(String.class))
                        .isInstanceOf(ResourceAccessException.class));
    }

    @Test
    void unServicioInalcanzableFallaRapidoConResourceAccessException() {
        RestClient cliente = new RestClientConfig().restClient(500, 500);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThatThrownBy(() -> cliente.get().uri("http://127.0.0.1:1/x").retrieve().body(String.class))
                        .isInstanceOf(ResourceAccessException.class));
    }

    @Test
    void patchSigueFuncionando() {
        RestClient cliente = new RestClientConfig().restClient(1000, 2000);

        String metodo = cliente.method(HttpMethod.PATCH).uri(base() + "/metodo").body("{}").retrieve().body(String.class);

        assertThat(metodo).isEqualTo("PATCH");
    }
}
