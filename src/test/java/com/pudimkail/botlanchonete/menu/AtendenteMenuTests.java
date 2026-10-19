package com.pudimkail.botlanchonete.menu;

import com.pudimkail.botlanchonete.pedido.Pedido;
import com.pudimkail.botlanchonete.pedido.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AtendenteMenuTests {

    @Autowired
    AtendenteMenu menu;
    @Autowired
    PedidoService pedidoService;

    @Test
    void pedidoCompletoComEntregaETroco() {
        String tel = "5565911110001";
        assertThat(menu.responder(tel, "oi")).contains("*1* - Ver cardápio", "*2* - Fazer pedido");
        assertThat(menu.responder(tel, "2")).contains("X-Bacon", "R$ 25,00");
        assertThat(menu.responder(tel, "x3 2 sem tomate")).contains("2x X-Bacon (sem tomate)", "Subtotal: R$ 50,00");
        assertThat(menu.responder(tel, "B1")).contains("Subtotal: R$ 56,00");
        assertThat(menu.responder(tel, "0")).contains("nome");
        assertThat(menu.responder(tel, "Ana")).contains("Prazer, Ana", "Entrega");
        assertThat(menu.responder(tel, "1")).contains("endereço");
        assertThat(menu.responder(tel, "Rua das Flores, 10, Centro")).contains("pagamento", "Dinheiro");
        assertThat(menu.responder(tel, "2")).contains("troco");
        assertThat(menu.responder(tel, "20")).contains("R$ 61,00", "valor maior");
        assertThat(menu.responder(tel, "100")).contains("Confira seu pedido", "Total: R$ 61,00", "Troco para R$ 100,00");

        String confirmacao = menu.responder(tel, "1");
        assertThat(confirmacao).contains("Pedido #", "registrado", "Total: R$ 61,00");

        Pedido salvo = pedidoService.listar().stream()
                .filter(p -> p.telefone().equals(tel)).findFirst().orElseThrow();
        assertThat(salvo.total()).isEqualByComparingTo(new BigDecimal("61.00"));
        assertThat(salvo.nomeCliente()).isEqualTo("Ana");
        assertThat(salvo.observacoes()).isEqualTo("Troco para R$ 100,00");

        // Depois do pedido, volta para o menu principal
        assertThat(menu.responder(tel, "oi")).contains("*1* - Ver cardápio");
    }

    @Test
    void codigoInvalidoECancelamento() {
        String tel = "5565911110002";
        menu.responder(tel, "2");
        assertThat(menu.responder(tel, "PIZZA")).contains("Não encontrei o código *PIZZA*");
        assertThat(menu.responder(tel, "0")).contains("carrinho está vazio");
        menu.responder(tel, "X1");
        assertThat(menu.responder(tel, "9")).contains("Pedido cancelado");
        assertThat(pedidoService.listar()).noneMatch(p -> p.telefone().equals(tel));
    }

    @Test
    void retiradaComPixSemTaxa() {
        String tel = "5565911110003";
        menu.responder(tel, "2");
        menu.responder(tel, "X1");
        menu.responder(tel, "0");
        menu.responder(tel, "Bia");
        assertThat(menu.responder(tel, "2")).contains("pagamento");
        String resumo = menu.responder(tel, "1");
        assertThat(resumo).contains("Retirada no balcão", "Total: R$ 18,00").doesNotContain("Taxa de entrega");
    }
}
