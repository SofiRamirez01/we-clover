package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    boolean existsByCodigoInterno(String codigoInterno);

    boolean existsByCodigoInternoAndIdNot(String codigoInterno, Long id);

    /** Filtro por estado de la Pantalla de Producción (ver ProduccionService). */
    List<Pedido> findByEstadoActualIn(List<EstadoPedido> estados);
}
