package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.TablaTalle;

public interface TablaTalleRepository extends JpaRepository<TablaTalle, Long> {

    boolean existsByGrupoTalleAndOrden(GrupoTalle grupoTalle, int orden);

    List<TablaTalle> findByGrupoTalleOrderByOrdenAsc(GrupoTalle grupoTalle);
}
