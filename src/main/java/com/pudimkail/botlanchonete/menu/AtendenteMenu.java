package com.pudimkail.botlanchonete.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pudimkail.botlanchonete.cardapio.Cardapio;
import com.pudimkail.botlanchonete.cardapio.CardapioService;
import com.pudimkail.botlanchonete.pedido.Dinheiro;
import com.pudimkail.botlanchonete.pedido.Pedido;
import com.pudimkail.botlanchonete.pedido.PedidoService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atendente grátis, sem IA: o cliente navega por um menu numerado e digita os códigos do cardápio.
 * Cada telefone tem uma sessão que guarda em que etapa do pedido o cliente está.
 */
@Service
public class AtendenteMenu {

    private static final Duration TEMPO_SESSAO = Duration.ofHours(2);
    private static final int QUANTIDADE_MAXIMA = 50;

    enum Etapa { MENU, ITENS, NOME, ENTREGA, ENDERECO, PAGAMENTO, TROCO, CONFIRMAR }

    private final CardapioService cardapioService;
    private final PedidoService pedidoService;
    private final ObjectMapper mapper;
    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();

    public AtendenteMenu(CardapioService cardapioService, PedidoService pedidoService, ObjectMapper mapper) {
        this.cardapioService = cardapioService;
        this.pedidoService = pedidoService;
        this.mapper = mapper;
    }

    public String responder(String telefone, String mensagem) {
        Sessao sessao = sessoes.compute(telefone, (tel, atual) ->
                atual == null || atual.expirou() ? new Sessao() : atual);

        synchronized (sessao) {
            sessao.ultimaAtividade = Instant.now();
            String texto = mensagem.trim();
            if (texto.equalsIgnoreCase("menu") || texto.equalsIgnoreCase("voltar")) {
                sessao.limpar();
                return menuPrincipal();
            }
            return switch (sessao.etapa) {
                case MENU -> menu(sessao, texto);
                case ITENS -> itens(sessao, texto);
                case NOME -> nome(sessao, texto);
                case ENTREGA -> entrega(sessao, texto);
                case ENDERECO -> endereco(sessao, texto);
                case PAGAMENTO -> pagamento(sessao, texto);
                case TROCO -> troco(telefone, sessao, texto);
                case CONFIRMAR -> confirmar(telefone, sessao, texto);
            };
        }
    }

    private String menu(Sessao sessao, String texto) {
        Cardapio cardapio = cardapioService.getCardapio();
        return switch (texto) {
            case "1" -> "📋 *Cardápio*\n\n" + cardapioService.comoTexto()
                    + "\n\nDigite *2* para fazer um pedido ou *menu* para ver as opções.";
            case "2" -> {
                sessao.etapa = Etapa.ITENS;
                yield "🛒 *Vamos montar seu pedido!*\n\n" + cardapioService.comoTexto() + "\n\n" + instrucoesItens();
            }
            case "3" -> "🕒 *Horário:* " + cardapio.horario()
                    + "\n🛵 *Taxa de entrega:* " + Dinheiro.formatar(cardapio.taxaEntrega())
                    + " (retirada no balcão não paga taxa)"
                    + "\n💳 *Pagamento:* " + String.join(", ", cardapio.formasPagamento())
                    + "\n\nDigite *menu* para ver as opções.";
            case "4" -> "Certo! Um atendente da lanchonete vai te responder aqui em breve. 🙂";
            default -> menuPrincipal();
        };
    }

    private String itens(Sessao sessao, String texto) {
        if (texto.equals("0")) {
            if (sessao.itens.isEmpty()) {
                return "Seu carrinho está vazio. " + instrucoesItens();
            }
            sessao.etapa = Etapa.NOME;
            return "Ótimo! Qual é o seu *nome*?";
        }
        if (texto.equals("9")) {
            sessao.limpar();
            return "Pedido cancelado.\n\n" + menuPrincipal();
        }

        // Formato: CÓDIGO [QUANTIDADE] [OBSERVAÇÃO], ex.: "X3 2 sem tomate"
        String[] partes = texto.split("\\s+");
        Optional<Cardapio.Item> item = cardapioService.buscarPorCodigo(partes[0]);
        if (item.isEmpty()) {
            return "Não encontrei o código *" + partes[0] + "* no cardápio.\n\n" + instrucoesItens();
        }
        int quantidade = 1;
        int inicioObservacao = 1;
        if (partes.length > 1 && partes[1].matches("\\d{1,3}")) {
            quantidade = Integer.parseInt(partes[1]);
            inicioObservacao = 2;
        }
        if (quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
            return "A quantidade precisa ser de 1 a " + QUANTIDADE_MAXIMA + ".";
        }
        String observacao = String.join(" ", Arrays.copyOfRange(partes, inicioObservacao, partes.length));

        sessao.itens.add(new ItemCarrinho(item.get(), quantidade, observacao));
        return "✅ Adicionado: %dx %s%s\n\n%s\n\nDigite outro código para adicionar mais, *0* para finalizar ou *9* para cancelar."
                .formatted(quantidade, item.get().nome(), observacao.isBlank() ? "" : " (" + observacao + ")",
                        carrinho(sessao));
    }

    private String nome(Sessao sessao, String texto) {
        if (texto.length() < 2) {
            return "Por favor, digite seu *nome*.";
        }
        sessao.nome = texto;
        sessao.etapa = Etapa.ENTREGA;
        return "Prazer, " + texto + "! Como vai ser?\n\n" + opcoesEntrega();
    }

    private String entrega(Sessao sessao, String texto) {
        switch (texto) {
            case "1" -> {
                sessao.tipoEntrega = "entrega";
                sessao.etapa = Etapa.ENDERECO;
                return "Qual é o *endereço completo*? (rua, número, bairro e um ponto de referência)";
            }
            case "2" -> {
                sessao.tipoEntrega = "retirada";
                sessao.endereco = "";
                sessao.etapa = Etapa.PAGAMENTO;
                return opcoesPagamento();
            }
            default -> {
                return "Digite *1* ou *2*:\n\n" + opcoesEntrega();
            }
        }
    }

    private String endereco(Sessao sessao, String texto) {
        if (texto.length() < 8) {
            return "Preciso do endereço completo: rua, número e bairro.";
        }
        sessao.endereco = texto;
        sessao.etapa = Etapa.PAGAMENTO;
        return opcoesPagamento();
    }

    private String pagamento(Sessao sessao, String texto) {
        List<String> formas = cardapioService.getCardapio().formasPagamento();
        int escolha = texto.matches("\\d{1,2}") ? Integer.parseInt(texto) : 0;
        if (escolha < 1 || escolha > formas.size()) {
            return "Opção inválida. " + opcoesPagamento();
        }
        sessao.formaPagamento = formas.get(escolha - 1);
        if (sessao.formaPagamento.equalsIgnoreCase("Dinheiro")) {
            sessao.etapa = Etapa.TROCO;
            return "Precisa de troco para quanto? Digite o valor (ex.: *50*) ou *0* se não precisa.";
        }
        sessao.etapa = Etapa.CONFIRMAR;
        return resumoParaConfirmar(sessao);
    }

    private String troco(String telefone, Sessao sessao, String texto) {
        BigDecimal valor;
        try {
            valor = new BigDecimal(texto.replace("R$", "").replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return "Digite só o valor, por exemplo *50*, ou *0* se não precisa de troco.";
        }
        if (valor.signum() > 0) {
            BigDecimal total = pedidoService.montar(telefone, dadosDoPedido(sessao)).total();
            if (valor.compareTo(total) < 0) {
                return "O total do pedido é " + Dinheiro.formatar(total) + ". Digite um valor maior ou *0*.";
            }
            sessao.observacoes = "Troco para " + Dinheiro.formatar(valor);
        }
        sessao.etapa = Etapa.CONFIRMAR;
        return resumoParaConfirmar(sessao);
    }

    private String confirmar(String telefone, Sessao sessao, String texto) {
        switch (texto) {
            case "1" -> {
                Pedido pedido = pedidoService.salvar(pedidoService.montar(telefone, dadosDoPedido(sessao)));
                sessao.limpar();
                return "✅ " + pedidoService.resumo(pedido) + "\n\nObrigado! Seu pedido já foi para a cozinha. 😋";
            }
            case "2" -> {
                sessao.limpar();
                return "Pedido cancelado.\n\n" + menuPrincipal();
            }
            default -> {
                return "Digite *1* para confirmar ou *2* para cancelar.";
            }
        }
    }

    private String resumoParaConfirmar(Sessao sessao) {
        Pedido previa = pedidoService.montar("", dadosDoPedido(sessao));
        return "📝 *Confira seu pedido, " + sessao.nome + ":*\n\n" + pedidoService.detalhes(previa)
                + "\n\n*1* - Confirmar pedido\n*2* - Cancelar";
    }

    /** Mesmo formato que a ferramenta da IA usa, para o PedidoService validar e calcular. */
    private ObjectNode dadosDoPedido(Sessao sessao) {
        ObjectNode dados = mapper.createObjectNode()
                .put("nome_cliente", sessao.nome)
                .put("tipo_entrega", sessao.tipoEntrega)
                .put("endereco", sessao.endereco)
                .put("forma_pagamento", sessao.formaPagamento)
                .put("observacoes", sessao.observacoes);
        ArrayNode itens = dados.putArray("itens");
        sessao.itens.forEach(i -> itens.addObject()
                .put("codigo", i.item().codigo())
                .put("quantidade", i.quantidade())
                .put("observacao", i.observacao()));
        return dados;
    }

    private String carrinho(Sessao sessao) {
        StringBuilder sb = new StringBuilder("🛒 *Seu carrinho:*\n");
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemCarrinho i : sessao.itens) {
            BigDecimal valor = i.item().preco().multiply(BigDecimal.valueOf(i.quantidade()));
            subtotal = subtotal.add(valor);
            sb.append("%dx %s - %s%n".formatted(i.quantidade(), i.item().nome(), Dinheiro.formatar(valor)));
        }
        return sb.append("Subtotal: ").append(Dinheiro.formatar(subtotal)).toString();
    }

    private String menuPrincipal() {
        return "Olá! 👋 Bem-vindo(a) à *" + cardapioService.getCardapio().nomeLanchonete() + "*.\n"
                + "Digite o número da opção:\n\n"
                + "*1* - Ver cardápio\n"
                + "*2* - Fazer pedido\n"
                + "*3* - Horário, entrega e pagamento\n"
                + "*4* - Falar com um atendente";
    }

    private static String instrucoesItens() {
        return "Digite o *código* do item. Para mais de um, coloque a quantidade depois: *X1 2*\n"
                + "Também dá para pedir uma observação: *X3 1 sem tomate*";
    }

    private String opcoesEntrega() {
        return "*1* - Entrega (taxa de " + Dinheiro.formatar(cardapioService.getCardapio().taxaEntrega()) + ")\n"
                + "*2* - Retirada no balcão";
    }

    private String opcoesPagamento() {
        List<String> formas = cardapioService.getCardapio().formasPagamento();
        StringBuilder sb = new StringBuilder("Qual a forma de *pagamento*?\n\n");
        for (int i = 0; i < formas.size(); i++) {
            sb.append('*').append(i + 1).append("* - ").append(formas.get(i)).append('\n');
        }
        return sb.toString().trim();
    }

    private record ItemCarrinho(Cardapio.Item item, int quantidade, String observacao) {
    }

    private static final class Sessao {
        Etapa etapa = Etapa.MENU;
        final List<ItemCarrinho> itens = new ArrayList<>();
        String nome = "";
        String tipoEntrega = "";
        String endereco = "";
        String formaPagamento = "";
        String observacoes = "";
        Instant ultimaAtividade = Instant.now();

        boolean expirou() {
            return ultimaAtividade.plus(TEMPO_SESSAO).isBefore(Instant.now());
        }

        void limpar() {
            etapa = Etapa.MENU;
            itens.clear();
            nome = tipoEntrega = endereco = formaPagamento = observacoes = "";
        }
    }
}
