package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.TipoTela;

public interface PaletaColoresRepository extends JpaRepository<PaletaColores, Long> {

    List<PaletaColores> findByActivoTrueOrderByNombreAsc();

    List<PaletaColores> findByActivoTrueAndTipoTela_CodigoOrderByNombreAsc(String codigoTipoTela);

    boolean existsByNombreIgnoreCaseAndTipoTela(String nombre, TipoTela tipoTela);

    Optional<PaletaColores> findByNombreIgnoreCaseAndTipoTela(String nombre, TipoTela tipoTela);
}
