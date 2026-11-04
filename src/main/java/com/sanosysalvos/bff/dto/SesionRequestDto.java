package com.sanosysalvos.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/*
 * tokens que el frontend obtuvo directo de Cognito, para que el bff los valide y los devuelva como cookies httpOnly.
 *
 * El refresh_token ya no forma parte de esto: el bff no tiene endpoint de refresco, así que guardarlo (y mandarlo
 * en cada llamada) solo ampliaba la superficie de exposición sin darle uso. Si el frontend todavía lo envía en el
 * JSON, simplemente se ignora (Jackson descarta las propiedades desconocidas).
 */
@Data
public class SesionRequestDto {

    @NotBlank(message = "accessToken es requerido")
    @Size(max = 8192, message = "accessToken demasiado largo")
    private String accessToken;

    @NotBlank(message = "idToken es requerido")
    @Size(max = 8192, message = "idToken demasiado largo")
    private String idToken;
}
