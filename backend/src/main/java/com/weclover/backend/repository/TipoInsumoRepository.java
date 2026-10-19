package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.TipoInsumo;

public interface TipoInsumoRepository extends JpaRepository<TipoInsumo, Long> {

    Optional<TipoInsumo> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<TipoInsumo> findByActivoTrueOrderByNombreAsc();
}
