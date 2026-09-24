package com.sanosysalvos.bff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/* respuesta de GET /api/v1/auth/me: datos minimos del usuario logueado, sacados del id token */
@Data
@AllArgsConstructor
public class UsuarioSesionDto {
    private String email;
    private String nombre;
}
