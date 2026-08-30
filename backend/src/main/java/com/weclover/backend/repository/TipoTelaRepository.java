package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.TipoTela;

public interface TipoTelaRepository extends JpaRepository<TipoTela, Long> {

    Optional<TipoTela> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<TipoTela> findByActivoTrueOrderByNombreAsc();
}
