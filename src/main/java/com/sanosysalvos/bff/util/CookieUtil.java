package com.sanosysalvos.bff.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/* arma y limpia las cookies httpOnly de sesion (access_token, id_token, refresh_token) */
public class CookieUtil {

    private CookieUtil() {
    }

    public static void setCookie(HttpServletResponse response, String nombre, String valor, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(true) /* los navegadores tratan localhost como contexto seguro, asi que funciona igual en dev por http */
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void clearCookie(HttpServletResponse response, String nombre) {
        ResponseCookie cookie = ResponseCookie.from(nombre, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static String leerCookie(HttpServletRequest request, String nombre) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (cookie.getName().equals(nombre)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
