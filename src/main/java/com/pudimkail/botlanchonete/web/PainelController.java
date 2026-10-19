package com.pudimkail.botlanchonete.web;

import com.pudimkail.botlanchonete.Atendimento;
import com.pudimkail.botlanchonete.pedido.Pedido;
import com.pudimkail.botlanchonete.pedido.PedidoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Endpoints do simulador (testar sem WhatsApp) e da lista de pedidos. */
@RestController
@RequestMapping("/api")
public class PainelController {

    private final Atendimento atendente;
    private final PedidoService pedidoService;

    public PainelController(Atendimento atendente, PedidoService pedidoService) {
        this.atendente = atendente;
        this.pedidoService = pedidoService;
    }

    public record MensagemSimulada(String telefone, String mensagem) {
    }

    @PostMapping("/simular")
    public Map<String, String> simular(@RequestBody MensagemSimulada msg) {
        return Map.of("resposta", atendente.responder(msg.telefone(), msg.mensagem()));
    }

    @GetMapping("/modo")
    public Map<String, String> modo() {
        return Map.of("modo", atendente.modo());
    }

    @GetMapping("/pedidos")
    public List<Pedido> pedidos() {
        return pedidoService.listar();
    }
}
