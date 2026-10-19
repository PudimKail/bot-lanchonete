package com.pudimkail.botlanchonete;

import com.pudimkail.botlanchonete.ia.AtendenteIA;
import com.pudimkail.botlanchonete.menu.AtendenteMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Escolhe quem atende: a IA (precisa de ANTHROPIC_API_KEY com crédito) ou o menu numerado (grátis).
 * bot.modo = auto | ia | menu. No "auto", usa a IA só se a chave estiver configurada.
 */
@Service
public class Atendimento {

    private static final Logger log = LoggerFactory.getLogger(Atendimento.class);

    private final AtendenteIA ia;
    private final AtendenteMenu menu;
    private final boolean usarIa;

    public Atendimento(AtendenteIA ia, AtendenteMenu menu, @Value("${bot.modo:auto}") String modo) {
        this.ia = ia;
        this.menu = menu;
        String chave = System.getenv("ANTHROPIC_API_KEY");
        this.usarIa = switch (modo) {
            case "ia" -> true;
            case "menu" -> false;
            default -> chave != null && !chave.isBlank();
        };
        log.info("Modo de atendimento: {}", modo());
    }

    public String responder(String telefone, String mensagem) {
        return usarIa ? ia.responder(telefone, mensagem) : menu.responder(telefone, mensagem);
    }

    public String modo() {
        return usarIa ? "ia" : "menu";
    }
}
