package com.sanosysalvos.bff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/* respuesta de GET /api/v1/auth/me: datos minimos del usuario logueado, sacados del id token.
 * esAdministrador es solo para que el frontend decida que mostrar (ej. ocultar el link al
 * panel de admin) -- no es una medida de seguridad, cada microservicio vuelve a validar el
 * grupo "Administradores" del JWT en cada request a sus endpoints de admin. */
@Data
@AllArgsConstructor
public class UsuarioSesionDto {
    private String email;
    private String nombre;
    private boolean esAdministrador;
}
