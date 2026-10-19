package com.pudimkail.botlanchonete.pedido;

import com.fasterxml.jackson.databind.JsonNode;
import com.pudimkail.botlanchonete.cardapio.Cardapio;
import com.pudimkail.botlanchonete.cardapio.CardapioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registra os pedidos no PostgreSQL. Os preços e o total são calculados aqui, a partir do cardápio,
 * e nunca pela IA: assim o cliente não recebe um valor errado.
 */
@Service
public class PedidoService {

    private final CardapioService cardapioService;
    private final PedidoRepository repository;

    public PedidoService(CardapioService cardapioService, PedidoRepository repository) {
        this.cardapioService = cardapioService;
        this.repository = repository;
    }

    /** Pedidos do mais novo para o mais antigo. */
    public List<Pedido> listar() {
        return repository.findAllByOrderByNumeroDesc().stream().map(PedidoEntity::paraPedido).toList();
    }

    /** Recebe o JSON que a IA mandou na ferramenta registrar_pedido e salva o pedido. */
    public Pedido registrar(String telefone, JsonNode dados) {
        return salvar(montar(telefone, dados));
    }

    public Pedido salvar(Pedido pedido) {
        return repository.save(PedidoEntity.de(pedido)).paraPedido();
    }

    /** Confere os itens e calcula o total, sem salvar. Dados no mesmo formato da ferramenta da IA. */
    public Pedido montar(String telefone, JsonNode dados) {
        List<Pedido.ItemPedido> itens = new ArrayList<>();
        for (JsonNode item : dados.path("itens")) {
            String codigo = item.path("codigo").asText();
            int quantidade = item.path("quantidade").asInt();
            if (quantidade < 1) {
                throw new IllegalArgumentException("Quantidade inválida para o item " + codigo);
            }
            Cardapio.Item doCardapio = cardapioService.buscarPorCodigo(codigo)
                    .orElseThrow(() -> new IllegalArgumentException("Item não existe no cardápio: " + codigo));
            itens.add(new Pedido.ItemPedido(doCardapio.codigo(), doCardapio.nome(), quantidade,
                    doCardapio.preco(), item.path("observacao").asText("")));
        }
        if (itens.isEmpty()) {
            throw new IllegalArgumentException("O pedido não tem itens");
        }

        String tipoEntrega = dados.path("tipo_entrega").asText();
        String endereco = dados.path("endereco").asText("");
        if (tipoEntrega.equals("entrega") && endereco.isBlank()) {
            throw new IllegalArgumentException("Pedido para entrega precisa de endereço");
        }

        BigDecimal taxa = tipoEntrega.equals("entrega") ? cardapioService.getCardapio().taxaEntrega() : BigDecimal.ZERO;
        BigDecimal total = itens.stream().map(Pedido.ItemPedido::subtotal).reduce(taxa, BigDecimal::add);

        return new Pedido(null, telefone, dados.path("nome_cliente").asText(),
                itens, tipoEntrega, endereco, dados.path("forma_pagamento").asText(),
                dados.path("observacoes").asText(""), taxa, total, LocalDateTime.now());
    }

    /** Resumo de um pedido já salvo, para confirmar com o cliente. */
    public String resumo(Pedido pedido) {
        return "Pedido #" + pedido.numero() + " registrado.\n" + detalhes(pedido);
    }

    /** Itens, taxa, total, entrega e pagamento. */
    public String detalhes(Pedido pedido) {
        StringBuilder sb = new StringBuilder();
        for (Pedido.ItemPedido item : pedido.itens()) {
            sb.append("%dx %s - %s%s%n".formatted(item.quantidade(), item.nome(), Dinheiro.formatar(item.subtotal()),
                    item.observacao().isBlank() ? "" : " (" + item.observacao() + ")"));
        }
        if (pedido.taxaEntrega().signum() > 0) {
            sb.append("Taxa de entrega: ").append(Dinheiro.formatar(pedido.taxaEntrega())).append('\n');
        }
        sb.append("*Total: ").append(Dinheiro.formatar(pedido.total())).append("*\n");
        sb.append(pedido.tipoEntrega().equals("entrega") ? "Entrega em: " + pedido.endereco() : "Retirada no balcão").append('\n');
        sb.append("Pagamento: ").append(pedido.formaPagamento());
        if (!pedido.observacoes().isBlank()) {
            sb.append('\n').append("Obs.: ").append(pedido.observacoes());
        }
        return sb.toString();
    }
}
