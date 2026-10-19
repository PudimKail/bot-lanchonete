package com.pudimkail.botlanchonete.pedido;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Tabela "pedidos" no PostgreSQL. Os itens ficam na tabela "pedido_itens". */
@Entity
@Table(name = "pedidos")
public class PedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer numero;

    private String telefone;
    private String nomeCliente;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pedido_itens", joinColumns = @JoinColumn(name = "pedido_numero"))
    @OrderColumn(name = "posicao")
    private List<Item> itens = new ArrayList<>();

    private String tipoEntrega;
    private String endereco;
    private String formaPagamento;
    private String observacoes;

    @Column(precision = 10, scale = 2)
    private BigDecimal taxaEntrega;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    private LocalDateTime criadoEm;

    protected PedidoEntity() {
    }

    static PedidoEntity de(Pedido pedido) {
        PedidoEntity e = new PedidoEntity();
        e.telefone = pedido.telefone();
        e.nomeCliente = pedido.nomeCliente();
        pedido.itens().forEach(i -> e.itens.add(new Item(i.codigo(), i.nome(), i.quantidade(), i.precoUnitario(), i.observacao())));
        e.tipoEntrega = pedido.tipoEntrega();
        e.endereco = pedido.endereco();
        e.formaPagamento = pedido.formaPagamento();
        e.observacoes = pedido.observacoes();
        e.taxaEntrega = pedido.taxaEntrega();
        e.total = pedido.total();
        e.criadoEm = pedido.criadoEm();
        return e;
    }

    Pedido paraPedido() {
        return new Pedido(numero, telefone, nomeCliente,
                itens.stream().map(i -> new Pedido.ItemPedido(i.codigo, i.nome, i.quantidade, i.precoUnitario, i.observacao)).toList(),
                tipoEntrega, endereco, formaPagamento, observacoes, taxaEntrega, total, criadoEm);
    }

    @Embeddable
    public static class Item {
        private String codigo;
        private String nome;
        private int quantidade;
        @Column(precision = 10, scale = 2)
        private BigDecimal precoUnitario;
        private String observacao;

        protected Item() {
        }

        Item(String codigo, String nome, int quantidade, BigDecimal precoUnitario, String observacao) {
            this.codigo = codigo;
            this.nome = nome;
            this.quantidade = quantidade;
            this.precoUnitario = precoUnitario;
            this.observacao = observacao;
        }
    }
}
