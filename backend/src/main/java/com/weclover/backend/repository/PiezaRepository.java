package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.Pieza;

public interface PiezaRepository extends JpaRepository<Pieza, Long> {

    List<Pieza> findByGrupoTalle_Id(Long idGrupoTalle);

    List<Pieza> findByActivoTrue();

    Optional<Pieza> findByGrupoTalle_IdAndNombre(Long idGrupoTalle, String nombre);

    /** Para validar unicidad de nombre al editar, sin chocar contra la propia Pieza que se edita. */
    Optional<Pieza> findByGrupoTalle_IdAndNombreAndIdNot(Long idGrupoTalle, String nombre, Long id);
}
