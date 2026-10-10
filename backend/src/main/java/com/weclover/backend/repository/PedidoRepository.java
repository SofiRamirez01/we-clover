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

    /** Pedidos de una tanda (ver TandaService). */
    List<Pedido> findByTanda_Id(Long idTanda);

    /** Todos los pedidos con tanda asignada, para derivar el estado de cada tanda en una sola
     *  consulta (ver TandaService). */
    List<Pedido> findByTandaIsNotNull();
}
