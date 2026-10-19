package com.pudimkail.botlanchonete.pedido;

import com.fasterxml.jackson.databind.JsonNode;
import com.pudimkail.botlanchonete.cardapio.Cardapio;
import com.pudimkail.botlanchonete.cardapio.CardapioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registra os pedidos. Os preços e o total são calculados aqui, a partir do cardápio,
 * e nunca pela IA: assim o cliente não recebe um valor errado.
 * Os pedidos ficam na memória (somem ao reiniciar). Próximo passo: salvar no PostgreSQL.
 */
@Service
public class PedidoService {

    private final CardapioService cardapioService;
    private final List<Pedido> pedidos = new CopyOnWriteArrayList<>();
    private final AtomicInteger proximoNumero = new AtomicInteger(1);

    public PedidoService(CardapioService cardapioService) {
        this.cardapioService = cardapioService;
    }

    public List<Pedido> listar() {
        return List.copyOf(pedidos);
    }

    /** Recebe o JSON que a IA mandou na ferramenta registrar_pedido. */
    public Pedido registrar(String telefone, JsonNode dados) {
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

        Pedido pedido = new Pedido(proximoNumero.getAndIncrement(), telefone, dados.path("nome_cliente").asText(),
                itens, tipoEntrega, endereco, dados.path("forma_pagamento").asText(),
                dados.path("observacoes").asText(""), taxa, total, LocalDateTime.now());
        pedidos.add(pedido);
        return pedido;
    }

    /** Resumo devolvido para a IA confirmar com o cliente. */
    public String resumo(Pedido pedido) {
        StringBuilder sb = new StringBuilder("Pedido #" + pedido.numero() + " registrado.\n");
        for (Pedido.ItemPedido item : pedido.itens()) {
            sb.append("%dx %s - R$ %s%s%n".formatted(item.quantidade(), item.nome(), item.subtotal(),
                    item.observacao().isBlank() ? "" : " (" + item.observacao() + ")"));
        }
        if (pedido.taxaEntrega().signum() > 0) {
            sb.append("Taxa de entrega: R$ ").append(pedido.taxaEntrega()).append('\n');
        }
        sb.append("Total: R$ ").append(pedido.total()).append('\n');
        sb.append(pedido.tipoEntrega().equals("entrega") ? "Entrega em: " + pedido.endereco() : "Retirada no balcão").append('\n');
        sb.append("Pagamento: ").append(pedido.formaPagamento());
        return sb.toString();
    }
}
