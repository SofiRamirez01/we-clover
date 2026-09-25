package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.HistorialEtapaProduccion;

@Repository
public interface HistorialEtapaProduccionRepository extends JpaRepository<HistorialEtapaProduccion, Long> {

    /** Para el historial unificado de un pedido (ver PedidoService.listarHistorial) — todas las
     *  etapas de todos los productos de ese pedido. */
    List<HistorialEtapaProduccion> findByProducto_Pedido_IdOrderByFechaCambioDesc(Long idPedido);
}
