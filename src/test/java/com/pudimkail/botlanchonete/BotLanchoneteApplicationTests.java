package com.pudimkail.botlanchonete;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pudimkail.botlanchonete.pedido.Pedido;
import com.pudimkail.botlanchonete.pedido.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"whatsapp.verify-token=segredo", "whatsapp.app-secret=chave-do-app"})
@AutoConfigureMockMvc
class BotLanchoneteApplicationTests {

    @Autowired
    PedidoService pedidoService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    MockMvc mvc;

    @Test
    void calculaTotalComTaxaDeEntrega() throws Exception {
        Pedido pedido = pedidoService.registrar("5565999990000", mapper.readTree("""
                {"nome_cliente": "Ana", "tipo_entrega": "entrega", "endereco": "Rua A, 10",
                 "forma_pagamento": "Pix", "observacoes": "",
                 "itens": [{"codigo": "X3", "quantidade": 2, "observacao": "sem tomate"},
                           {"codigo": "b1", "quantidade": 1, "observacao": ""}]}
                """));

        // 2 x 25,00 + 6,00 + 5,00 de entrega
        assertThat(pedido.total()).isEqualByComparingTo(new BigDecimal("61.00"));
        assertThat(pedidoService.resumo(pedido)).contains("Total: R$ 61.00", "2x X-Bacon", "sem tomate");

        // O pedido foi salvo no banco com número e itens
        assertThat(pedido.numero()).isNotNull();
        Pedido salvo = pedidoService.listar().stream()
                .filter(p -> p.numero().equals(pedido.numero())).findFirst().orElseThrow();
        assertThat(salvo.itens()).hasSize(2);
        assertThat(salvo.itens().get(0).observacao()).isEqualTo("sem tomate");
    }

    @Test
    void retiradaNaoPagaTaxa() throws Exception {
        Pedido pedido = pedidoService.registrar("5565999990000", mapper.readTree("""
                {"nome_cliente": "Bia", "tipo_entrega": "retirada", "endereco": "",
                 "forma_pagamento": "Dinheiro", "observacoes": "troco para 50",
                 "itens": [{"codigo": "X1", "quantidade": 1, "observacao": ""}]}
                """));

        assertThat(pedido.total()).isEqualByComparingTo(new BigDecimal("18.00"));
    }

    @Test
    void recusaItemForaDoCardapio() {
        assertThatThrownBy(() -> pedidoService.registrar("5565999990000", mapper.readTree("""
                {"nome_cliente": "Caio", "tipo_entrega": "retirada", "endereco": "",
                 "forma_pagamento": "Pix", "observacoes": "",
                 "itens": [{"codigo": "PIZZA", "quantidade": 1, "observacao": ""}]}
                """))).hasMessageContaining("não existe no cardápio");
    }

    @Test
    void entregaSemEnderecoEhRecusada() {
        assertThatThrownBy(() -> pedidoService.registrar("5565999990000", mapper.readTree("""
                {"nome_cliente": "Davi", "tipo_entrega": "entrega", "endereco": "",
                 "forma_pagamento": "Pix", "observacoes": "",
                 "itens": [{"codigo": "X1", "quantidade": 1, "observacao": ""}]}
                """))).hasMessageContaining("endereço");
    }

    @Test
    void webhookVerificaToken() throws Exception {
        mvc.perform(get("/webhook").param("hub.mode", "subscribe")
                        .param("hub.verify_token", "segredo").param("hub.challenge", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("12345"));

        mvc.perform(get("/webhook").param("hub.mode", "subscribe")
                        .param("hub.verify_token", "errado").param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookRecusaAssinaturaInvalida() throws Exception {
        mvc.perform(post("/webhook").content("{\"entry\": []}")
                        .header("X-Hub-Signature-256", "sha256=000"))
                .andExpect(status().isUnauthorized());
    }
}
