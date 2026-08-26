package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.PatronCorte;

public interface PatronCorteRepository extends JpaRepository<PatronCorte, Long> {

    List<PatronCorte> findByActivoTrueOrderByNombreAsc();

    boolean existsByNumeroInterno(Integer numeroInterno);
}
