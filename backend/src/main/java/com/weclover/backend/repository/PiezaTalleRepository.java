package com.weclover.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.weclover.backend.entity.PiezaTalle;

public interface PiezaTalleRepository extends JpaRepository<PiezaTalle, Long> {

    List<PiezaTalle> findByPieza_Id(Long idPieza);

    Optional<PiezaTalle> findByPieza_IdAndTalle_Id(Long idPieza, Long idTalle);

    Optional<PiezaTalle> findByPieza_IdAndEsBaseTrue(Long idPieza);

    /** Para armar el listado (PiezaResponse.anchoBaseCm/largoBaseCm) sin consultar fila por fila. */
    List<PiezaTalle> findByPieza_IdInAndEsBaseTrue(List<Long> idsPieza);

    /** Preparación del corte: la geometría de todos los talles de esas piezas, con su talle
     *  (la clave de agrupación lo necesita), en una sola consulta. */
    @Query("SELECT pt FROM PiezaTalle pt JOIN FETCH pt.talle WHERE pt.pieza.id IN :idsPieza")
    List<PiezaTalle> buscarConTallePorPiezas(@Param("idsPieza") Collection<Long> idsPieza);
}
