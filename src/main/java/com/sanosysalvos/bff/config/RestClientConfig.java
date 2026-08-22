package com.sanosysalvos.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    /*rest client es para hacer las llamadas http y como bean para inyectarlas donde necesitemos hacer esas llamadas */
    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}




