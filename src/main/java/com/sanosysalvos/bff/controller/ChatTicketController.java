package com.sanosysalvos.bff.controller;

import com.sanosysalvos.bff.dto.TicketChatDto;
import com.sanosysalvos.bff.service.ChatTicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * Ruta propia (mas especifica que el /** del ProxyController, asi que gana): este endpoint no se
 * reenvia a ningun microservicio, lo resuelve el bff porque es quien valida la sesion.
 * Exige sesion (cualquier ruta que no es permitAll lo hace) y el header anti-CSRF por ser POST.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatTicketController {

    private final ChatTicketService chatTicketService;

    @PostMapping("/ticket")
    public ResponseEntity<TicketChatDto> emitirTicket(HttpServletRequest request) {
        /* JwtFilter lo deja como atributo despues de validar access token + id token */
        String sub = (String) request.getAttribute("X-User-Id");
        if (sub == null || sub.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        return chatTicketService.emitir(sub)
                .map(ticket -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(new TicketChatDto(ticket, chatTicketService.wsUrl(), chatTicketService.vigenciaSegundos())))
                .orElseGet(() -> ResponseEntity.status(503).build()); /* chat sin configurar */
    }
}
