package com.weclover.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.NotaPedido;

@Repository
public interface NotaPedidoRepository extends JpaRepository<NotaPedido, Long> {

    List<NotaPedido> findByPedidoIdOrderByFechaDescIdDesc(Long idPedido);

    /** Notas de varios pedidos en una sola consulta (grilla de Producción), más nueva primero. */
    List<NotaPedido> findByPedidoIdInOrderByFechaDescIdDesc(Collection<Long> idsPedidos);
}
