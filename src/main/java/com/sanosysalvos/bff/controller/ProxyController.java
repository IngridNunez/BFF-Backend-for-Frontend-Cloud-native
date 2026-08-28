package com.sanosysalvos.bff.controller;

import com.sanosysalvos.bff.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* captura todas las peticiones y las reenvía al ms correspondiente */
@RestController
@RequestMapping("/api/v1") /*cambiar */
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService; /* service que maneja el proxy */

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return proxyService.proxy(request, body); /* delega al service */
    }
}

// pull en actions se crea un archivo .env y se configura a nivel de repositorio, 



