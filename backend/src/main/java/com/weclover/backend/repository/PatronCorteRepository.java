package com.weclover.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.PatronCorte;

public interface PatronCorteRepository extends JpaRepository<PatronCorte, Long> {

    List<PatronCorte> findByActivoTrueOrderByNombreAsc();

    List<PatronCorte> findByActivoTrueAndTiposPrenda_IdOrderByNombreAsc(Long idTipoPrenda);

    /** Usado para validar unicidad de numeroInterno por tipo de prenda (no global, ver PatronCorte.numeroInterno). */
    List<PatronCorte> findDistinctByNumeroInternoAndTiposPrenda_IdIn(Integer numeroInterno, Collection<Long> idsTipoPrenda);
}
