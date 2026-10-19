package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.ProductoInsumoPieza;

public interface ProductoInsumoPiezaRepository extends JpaRepository<ProductoInsumoPieza, Long> {

    /** Asignaciones (de cualquier producto) que apuntan a un pin — para descartarlas cuando el
     *  pin se borra o cambia de pieza (ver PatronCortePosicionPiezaService). */
    List<ProductoInsumoPieza> findByPosicionPieza_Id(Long idPosicionPieza);

    /** Asignaciones a cualquier pin de una pieza — para descartarlas cuando la pieza cambia de
     *  tipo de insumo (ver PiezaService.actualizar). */
    List<ProductoInsumoPieza> findByPosicionPieza_Pieza_Id(Long idPieza);
}
