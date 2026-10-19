package com.pudimkail.botlanchonete.pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Pedido(
        Integer numero,
        String telefone,
        String nomeCliente,
        List<ItemPedido> itens,
        String tipoEntrega,
        String endereco,
        String formaPagamento,
        String observacoes,
        BigDecimal taxaEntrega,
        BigDecimal total,
        LocalDateTime criadoEm) {

    public record ItemPedido(String codigo, String nome, int quantidade, BigDecimal precoUnitario, String observacao) {
        public BigDecimal subtotal() {
            return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
        }
    }
}
