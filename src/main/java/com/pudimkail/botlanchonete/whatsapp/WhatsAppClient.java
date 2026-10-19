package com.pudimkail.botlanchonete.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** Envia mensagens de texto pela WhatsApp Cloud API (Meta). */
@Component
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final RestClient http;
    private final String phoneNumberId;
    private final boolean configurado;

    public WhatsAppClient(@Value("${whatsapp.token}") String token,
                          @Value("${whatsapp.phone-number-id}") String phoneNumberId,
                          @Value("${whatsapp.api-version}") String apiVersion) {
        this.phoneNumberId = phoneNumberId;
        this.configurado = !token.isBlank() && !phoneNumberId.isBlank();
        this.http = RestClient.builder()
                .baseUrl("https://graph.facebook.com/" + apiVersion)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    public void enviarTexto(String para, String texto) {
        if (!configurado) {
            log.warn("WhatsApp não configurado; mensagem para {} não enviada: {}", para, texto);
            return;
        }
        try {
            http.post()
                    .uri("/{id}/messages", phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "to", para,
                            "type", "text",
                            "text", Map.of("body", texto)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Falha ao enviar mensagem para {}", para, e);
        }
    }
}
