package com.weclover.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.CargaTallesPedido;
import com.weclover.backend.entity.Pedido;

public interface CargaTallesPedidoRepository extends JpaRepository<CargaTallesPedido, Long> {

    Optional<CargaTallesPedido> findByPedido(Pedido pedido);

    Optional<CargaTallesPedido> findByToken(String token);
}
