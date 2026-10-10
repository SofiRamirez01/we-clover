package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.Tanda;

@Repository
public interface TandaRepository extends JpaRepository<Tanda, Long> {

    /** Todas las tandas en orden de cola (el desempate por id mantiene estable la posición de
     *  dos tandas con el mismo `orden`). Cuáles están cerradas se deriva después (ver
     *  TandaEstadoCalculador). */
    List<Tanda> findAllByOrderByOrdenAscIdAsc();
}
