package com.pudimkail.botlanchonete.cardapio;

import java.math.BigDecimal;
import java.util.List;

public record Cardapio(
        String nomeLanchonete,
        String horario,
        BigDecimal taxaEntrega,
        List<String> formasPagamento,
        List<Item> itens) {

    public record Item(String codigo, String nome, String categoria, BigDecimal preco, String descricao) {
    }
}
