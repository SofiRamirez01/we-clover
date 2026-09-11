package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.PiezaTalle;

public interface PiezaTalleRepository extends JpaRepository<PiezaTalle, Long> {

    List<PiezaTalle> findByPieza_Id(Long idPieza);

    Optional<PiezaTalle> findByPieza_IdAndTalle_Id(Long idPieza, Long idTalle);

    Optional<PiezaTalle> findByPieza_IdAndEsBaseTrue(Long idPieza);

    /** Para armar el listado (PiezaResponse.anchoBaseCm/largoBaseCm) sin consultar fila por fila. */
    List<PiezaTalle> findByPieza_IdInAndEsBaseTrue(List<Long> idsPieza);
}
