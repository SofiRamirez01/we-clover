package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.HistorialUbicacionPedido;

@Repository
public interface HistorialUbicacionPedidoRepository extends JpaRepository<HistorialUbicacionPedido, Long> {

    /** Para el historial unificado de un pedido (ver PedidoService.listarHistorial). */
    List<HistorialUbicacionPedido> findByPedidoIdOrderByFechaDesc(Long idPedido);
}
