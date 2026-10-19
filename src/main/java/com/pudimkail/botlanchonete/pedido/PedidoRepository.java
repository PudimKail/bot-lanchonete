package com.pudimkail.botlanchonete.pedido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<PedidoEntity, Integer> {

    List<PedidoEntity> findAllByOrderByNumeroDesc();
}
