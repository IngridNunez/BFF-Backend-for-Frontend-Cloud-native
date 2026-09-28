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

    /*
     * "secure" se calcula del request real (request.isSecure()) en vez de
     * venir siempre en true: en dev local todo corre por http, y una cookie
     * Secure sobre una conexion no-https el navegador la descarta en
     * silencio (nunca la vuelve a mandar) - la cookie se "guardaba" bien del
     * lado del servidor pero jamas volvia. En produccion (https) esto sera
     * true automaticamente.
     */
    public static void setCookie(HttpServletResponse response, String nombre, String valor, Duration maxAge, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void clearCookie(HttpServletResponse response, String nombre, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(nombre, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /*
     * Lee el header Cookie crudo en vez de usar request.getCookies(): el
     * parser estricto de Tomcat 11 (Rfc6265CookieProcessor, sin forma de
     * relajarlo - LegacyCookieProcessor ya no existe en esta version)
     * descarta el header COMPLETO si encuentra una sola cookie de terceros
     * con formato invalido (ej. "g_state" que pone Google Identity en el
     * navegador). Parseando el header a mano evitamos depender de ese
     * parser para las cookies que sí controlamos nosotros.
     */
    public static String leerCookie(HttpServletRequest request, String nombre) {
        String header = request.getHeader("Cookie");
        if (header == null) {
            return null;
        }
        for (String parte : header.split(";")) {
            String par = parte.strip();
            int igual = par.indexOf('=');
            if (igual < 0) {
                continue;
            }
            String clave = par.substring(0, igual).strip();
            if (clave.equals(nombre)) {
                return par.substring(igual + 1).strip();
            }
        }
        return null;
    }
}
