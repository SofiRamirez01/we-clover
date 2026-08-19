package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.HistorialEstadoPedido;

@Repository
public interface HistorialEstadoPedidoRepository extends JpaRepository<HistorialEstadoPedido, Long> {

    List<HistorialEstadoPedido> findByPedidoIdOrderByFechaCambioDesc(Long idPedido);
}
