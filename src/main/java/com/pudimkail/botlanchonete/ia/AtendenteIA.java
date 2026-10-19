package com.pudimkail.botlanchonete.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pudimkail.botlanchonete.cardapio.Cardapio;
import com.pudimkail.botlanchonete.cardapio.CardapioService;
import com.pudimkail.botlanchonete.pedido.Pedido;
import com.pudimkail.botlanchonete.pedido.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * O atendente: guarda a conversa de cada cliente (por telefone), manda para o Claude
 * e executa a ferramenta registrar_pedido quando o cliente confirma o pedido.
 */
@Service
public class AtendenteIA {

    private static final Logger log = LoggerFactory.getLogger(AtendenteIA.class);

    /** Depois desse tempo sem mensagens, o próximo "oi" começa uma conversa nova. */
    private static final Duration TEMPO_CONVERSA = Duration.ofHours(2);
    /** Limite de idas e voltas com a ferramenta numa mesma resposta. */
    private static final int MAX_PASSOS = 5;

    private static final String MSG_ERRO =
            "Desculpe, tive um probleminha aqui. Pode mandar sua mensagem de novo em instantes?";

    private final CardapioService cardapioService;
    private final PedidoService pedidoService;
    private final ObjectMapper mapper;
    private final String modelo;
    private final String promptSistema;
    private final Tool ferramentaPedido = criarFerramentaPedido();
    private final Map<String, Conversa> conversas = new ConcurrentHashMap<>();
    private volatile AnthropicClient client;

    public AtendenteIA(CardapioService cardapioService, PedidoService pedidoService, ObjectMapper mapper,
                       @Value("${ia.modelo}") String modelo) {
        this.cardapioService = cardapioService;
        this.pedidoService = pedidoService;
        this.mapper = mapper;
        this.modelo = modelo;
        this.promptSistema = montarPromptSistema(cardapioService.getCardapio());
    }

    /** Responde uma mensagem do cliente. Mensagens do mesmo telefone são atendidas uma por vez. */
    public String responder(String telefone, String mensagem) {
        Conversa conversa = conversas.compute(telefone, (tel, atual) ->
                atual == null || atual.expirou() ? new Conversa() : atual);

        synchronized (conversa) {
            conversa.ultimaAtividade = Instant.now();
            conversa.historico.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(mensagem)
                    .build());
            try {
                return conversar(telefone, conversa.historico);
            } catch (AnthropicException e) {
                log.error("Erro ao chamar a IA para {}", telefone, e);
                return MSG_ERRO;
            }
        }
    }

    private String conversar(String telefone, List<MessageParam> historico) {
        for (int passo = 0; passo < MAX_PASSOS; passo++) {
            Message resposta = cliente().messages().create(MessageCreateParams.builder()
                    .model(modelo)
                    .maxTokens(8000L)
                    .system(promptSistema)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .addTool(ferramentaPedido)
                    .messages(historico)
                    // Cache automático do prompt e da conversa: deixa as próximas mensagens mais baratas.
                    .cacheControl(CacheControlEphemeral.builder().build())
                    // Se o modelo recusar uma mensagem, a API tenta de novo com outro modelo.
                    .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .build());

            // A resposta inteira volta para o histórico (inclusive os blocos de raciocínio).
            historico.add(resposta.toParam());

            StopReason motivo = resposta.stopReason().orElse(StopReason.END_TURN);
            if (motivo.equals(StopReason.REFUSAL)) {
                log.warn("Resposta recusada para {}: {}", telefone, resposta.stopDetails());
                return "Desculpe, não consigo ajudar com isso. Posso te mostrar o cardápio ou anotar um pedido?";
            }
            if (!motivo.equals(StopReason.TOOL_USE)) {
                return texto(resposta);
            }

            List<ContentBlockParam> resultados = new ArrayList<>();
            for (ContentBlock bloco : resposta.content()) {
                bloco.toolUse().ifPresent(uso -> resultados.add(executarFerramenta(telefone, uso)));
            }
            historico.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(resultados)
                    .build());
        }
        log.warn("Limite de passos atingido para {}", telefone);
        return MSG_ERRO;
    }

    private ContentBlockParam executarFerramenta(String telefone, ToolUseBlock uso) {
        ToolResultBlockParam.Builder resultado = ToolResultBlockParam.builder().toolUseId(uso.id());
        if (!uso.name().equals("registrar_pedido")) {
            resultado.content("Ferramenta desconhecida: " + uso.name()).isError(true);
        } else {
            try {
                JsonNode dados = mapper.readTree(mapper.writeValueAsString(uso._input()));
                Pedido pedido = pedidoService.registrar(telefone, dados);
                log.info("Novo pedido #{} de {} - total R$ {}", pedido.numero(), telefone, pedido.total());
                resultado.content(pedidoService.resumo(pedido));
            } catch (IllegalArgumentException e) {
                resultado.content("Pedido não registrado: " + e.getMessage()).isError(true);
            } catch (Exception e) {
                log.error("Erro ao registrar pedido de {}", telefone, e);
                resultado.content("Erro interno ao registrar o pedido. Peça desculpas e tente de novo.").isError(true);
            }
        }
        return ContentBlockParam.ofToolResult(resultado.build());
    }

    private static String texto(Message resposta) {
        String texto = resposta.content().stream()
                .flatMap(bloco -> bloco.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining("\n"))
                .trim();
        return texto.isEmpty() ? MSG_ERRO : texto;
    }

    private AnthropicClient cliente() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.fromEnv(); // lê ANTHROPIC_API_KEY
                }
            }
        }
        return client;
    }

    private static Tool criarFerramentaPedido() {
        Map<String, Object> item = Map.of(
                "type", "object",
                "properties", Map.of(
                        "codigo", Map.of("type", "string", "description", "Código do item no cardápio, ex.: X1"),
                        "quantidade", Map.of("type", "integer", "description", "Quantidade, 1 ou mais"),
                        "observacao", Map.of("type", "string", "description", "Ex.: sem cebola. Vazio se não houver")),
                "required", List.of("codigo", "quantidade", "observacao"),
                "additionalProperties", false);

        return Tool.builder()
                .name("registrar_pedido")
                .description("Registra o pedido na cozinha. Use somente depois que o cliente confirmou "
                        + "os itens, o nome, a forma de entrega e a forma de pagamento. "
                        + "Devolve o resumo com o número do pedido e o total calculado pelo sistema.")
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("nome_cliente", JsonValue.from(Map.of("type", "string")))
                                .putAdditionalProperty("itens", JsonValue.from(Map.of("type", "array", "items", item)))
                                .putAdditionalProperty("tipo_entrega", JsonValue.from(Map.of(
                                        "type", "string", "enum", List.of("entrega", "retirada"))))
                                .putAdditionalProperty("endereco", JsonValue.from(Map.of(
                                        "type", "string", "description", "Endereço completo. Vazio se for retirada")))
                                .putAdditionalProperty("forma_pagamento", JsonValue.from(Map.of("type", "string")))
                                .putAdditionalProperty("observacoes", JsonValue.from(Map.of(
                                        "type", "string", "description", "Ex.: troco para 50. Vazio se não houver")))
                                .build())
                        .required(List.of("nome_cliente", "itens", "tipo_entrega", "endereco",
                                "forma_pagamento", "observacoes"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    private String montarPromptSistema(Cardapio cardapio) {
        return """
                Você é o atendente virtual da %s no WhatsApp. Fale em português do Brasil, de um jeito \
                simpático e direto, com mensagens curtas como numa conversa de WhatsApp. Use *negrito* \
                do WhatsApp com moderação e no máximo um emoji por mensagem.

                Seu trabalho é tirar dúvidas sobre o cardápio e anotar pedidos.

                Horário de funcionamento: %s
                Taxa de entrega: R$ %s (retirada no balcão não paga taxa)
                Formas de pagamento: %s

                # Cardápio
                %s

                # Como anotar um pedido
                1. Ofereça apenas itens do cardápio acima e use os preços dele. Se pedirem algo que não \
                existe, diga que não temos e sugira algo parecido.
                2. Pergunte o que faltar: itens e quantidades, nome do cliente, entrega ou retirada, \
                endereço (se for entrega) e forma de pagamento. Se for dinheiro, pergunte se precisa de troco.
                3. Antes de registrar, mostre um resumo do pedido e peça para o cliente confirmar.
                4. Só depois do "sim" do cliente, use a ferramenta registrar_pedido.
                5. Depois de registrar, envie ao cliente o resumo que a ferramenta devolveu, com o número \
                do pedido e o total. O total certo é sempre o da ferramenta.
                6. Se a ferramenta devolver erro, explique o problema ao cliente e corrija o pedido com ele.

                Não invente promoções, prazos de entrega nem informações que não estão aqui. Se o cliente \
                pedir para falar com uma pessoa ou tiver uma reclamação, diga que um atendente da \
                lanchonete vai responder em breve.
                """.formatted(cardapio.nomeLanchonete(), cardapio.horario(), cardapio.taxaEntrega(),
                String.join(", ", cardapio.formasPagamento()), cardapioService.comoTexto());
    }

    private static final class Conversa {
        final List<MessageParam> historico = new ArrayList<>();
        Instant ultimaAtividade = Instant.now();

        boolean expirou() {
            return ultimaAtividade.plus(TEMPO_CONVERSA).isBefore(Instant.now());
        }
    }
}
