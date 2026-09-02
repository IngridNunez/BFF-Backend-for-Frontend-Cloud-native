package com.sanosysalvos.bff.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component /*component que spring registra como bean para que se pieda inyectar en otras clases  */
@ConfigurationProperties(prefix = "servicios") /*busca en el yaml la seccion servicios y mapea lo que encuentra */
@Getter
@Setter
public class ServiciosProperties {

    private Map<String, String> rutas; /*variable para almacenar las rutas de los servicios */


}


