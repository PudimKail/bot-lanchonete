package com.pudimkail.botlanchonete.whatsapp;

import com.pudimkail.botlanchonete.ia.AtendenteIA;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Atende as mensagens do WhatsApp em segundo plano, para o webhook responder rápido à Meta. */
@Service
public class ProcessadorMensagens {

    private final AtendenteIA atendente;
    private final WhatsAppClient whatsApp;

    public ProcessadorMensagens(AtendenteIA atendente, WhatsAppClient whatsApp) {
        this.atendente = atendente;
        this.whatsApp = whatsApp;
    }

    @Async
    public void processar(String telefone, String texto) {
        whatsApp.enviarTexto(telefone, atendente.responder(telefone, texto));
    }

    @Async
    public void avisarTipoNaoSuportado(String telefone) {
        whatsApp.enviarTexto(telefone, "Por enquanto eu só entendo mensagens de texto. Pode escrever, por favor?");
    }
}
