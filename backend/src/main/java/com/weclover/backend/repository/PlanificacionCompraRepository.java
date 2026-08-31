package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.PlanificacionCompra;

public interface PlanificacionCompraRepository extends JpaRepository<PlanificacionCompra, Long> {

    List<PlanificacionCompra> findAllByOrderByFechaCreacionDesc();
}
