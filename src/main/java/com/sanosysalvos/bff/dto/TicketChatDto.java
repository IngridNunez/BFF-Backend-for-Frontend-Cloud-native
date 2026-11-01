package com.sanosysalvos.bff.dto;

/* respuesta de POST /api/v1/chat/ticket: el ticket de un solo uso y a dónde conectarse con él */
public record TicketChatDto(String ticket, String wsUrl, long expiraEnSegundos) {
}
