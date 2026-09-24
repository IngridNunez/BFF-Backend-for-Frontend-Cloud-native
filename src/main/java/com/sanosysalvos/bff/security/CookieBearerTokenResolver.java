package com.sanosysalvos.bff.security;

import com.sanosysalvos.bff.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/* en vez de leer el access token del header Authorization, lo lee de la cookie httpOnly access_token */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        return CookieUtil.leerCookie(request, "access_token");
    }
}
