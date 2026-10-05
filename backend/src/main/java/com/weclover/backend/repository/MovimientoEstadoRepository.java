package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.MovimientoEstado;

@Repository
public interface MovimientoEstadoRepository extends JpaRepository<MovimientoEstado, Long> {

    /** Para el historial unificado de un pedido (ver PedidoService.listarHistorial) — todos los
     *  movimientos de todos los productos de ese pedido. */
    List<MovimientoEstado> findByProducto_Pedido_IdOrderByFechaHoraDesc(Long idPedido);
}
