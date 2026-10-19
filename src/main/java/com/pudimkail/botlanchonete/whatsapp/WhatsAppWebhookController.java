package com.pudimkail.botlanchonete.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook que a Meta chama quando chega mensagem no WhatsApp da lanchonete.
 * GET  /webhook -> verificação feita uma vez, ao cadastrar o webhook no painel da Meta
 * POST /webhook -> mensagens recebidas
 */
@RestController
@RequestMapping("/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final ObjectMapper mapper;
    private final ProcessadorMensagens processador;
    private final String verifyToken;
    private final String appSecret;
    /** A Meta pode reenviar a mesma mensagem; guardamos os ids para não responder duas vezes. */
    private final Set<String> idsProcessados = ConcurrentHashMap.newKeySet();

    public WhatsAppWebhookController(ObjectMapper mapper, ProcessadorMensagens processador,
                                     @Value("${whatsapp.verify-token}") String verifyToken,
                                     @Value("${whatsapp.app-secret}") String appSecret) {
        this.mapper = mapper;
        this.processador = processador;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
    }

    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam("hub.mode") String modo,
                                            @RequestParam("hub.verify_token") String token,
                                            @RequestParam("hub.challenge") String desafio) {
        if ("subscribe".equals(modo) && verifyToken.equals(token)) {
            return ResponseEntity.ok(desafio);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receber(@RequestBody String corpo,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false) String assinatura)
            throws Exception {
        if (!assinaturaValida(corpo, assinatura)) {
            log.warn("Webhook recusado: assinatura inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode json = mapper.readTree(corpo);
        for (JsonNode entrada : json.path("entry")) {
            for (JsonNode mudanca : entrada.path("changes")) {
                for (JsonNode msg : mudanca.path("value").path("messages")) {
                    String id = msg.path("id").asText();
                    String de = msg.path("from").asText();
                    if (!idsProcessados.add(id)) {
                        continue;
                    }
                    if ("text".equals(msg.path("type").asText())) {
                        processador.processar(de, msg.path("text").path("body").asText());
                    } else {
                        processador.avisarTipoNaoSuportado(de);
                    }
                }
            }
        }
        // Responder 200 rápido; a resposta ao cliente é enviada em segundo plano.
        return ResponseEntity.ok().build();
    }

    /** Confere se o webhook veio mesmo da Meta. Sem app-secret configurado, a conferência é pulada. */
    private boolean assinaturaValida(String corpo, String assinatura) throws Exception {
        if (appSecret.isBlank()) {
            return true;
        }
        if (assinatura == null || !assinatura.startsWith("sha256=")) {
            return false;
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String esperado = "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), assinatura.getBytes(StandardCharsets.UTF_8));
    }
}
