package com.pudimkail.botlanchonete.cardapio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pudimkail.botlanchonete.pedido.Dinheiro;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Collectors;

/** Carrega o cardápio de src/main/resources/cardapio.json. Para outra lanchonete, basta trocar esse arquivo. */
@Service
public class CardapioService {

    private final Cardapio cardapio;

    public CardapioService(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("cardapio.json").getInputStream()) {
            this.cardapio = mapper.readValue(in, Cardapio.class);
        }
    }

    public Cardapio getCardapio() {
        return cardapio;
    }

    public Optional<Cardapio.Item> buscarPorCodigo(String codigo) {
        return cardapio.itens().stream()
                .filter(item -> item.codigo().equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    /** Cardápio em texto, no formato do WhatsApp. Usado pelo menu e pelo prompt da IA. */
    public String comoTexto() {
        return cardapio.itens().stream()
                .collect(Collectors.groupingBy(Cardapio.Item::categoria, java.util.LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(categoria -> "*" + categoria.getKey() + "*\n" + categoria.getValue().stream()
                        .map(i -> "*%s* - %s - %s\n      %s".formatted(i.codigo(), i.nome(), Dinheiro.formatar(i.preco()), i.descricao()))
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
    }
}
