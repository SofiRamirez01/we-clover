package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.PlanificacionCompraDetalle;

public interface PlanificacionCompraDetalleRepository extends JpaRepository<PlanificacionCompraDetalle, Long> {

    /** Usado para el badge "Ya en Planificación #X" en la pantalla de productos elegibles. */
    List<PlanificacionCompraDetalle> findByProducto_IdIn(List<Long> idsProducto);
}
