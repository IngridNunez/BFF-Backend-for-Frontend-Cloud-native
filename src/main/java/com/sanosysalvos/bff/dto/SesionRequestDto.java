package com.sanosysalvos.bff.dto;

import lombok.Data;

/* tokens que el frontend obtuvo directo de Cognito, para que el bff los valide y los devuelva como cookies httpOnly */
@Data
public class SesionRequestDto {
    private String accessToken;
    private String idToken;
    private String refreshToken;
}
