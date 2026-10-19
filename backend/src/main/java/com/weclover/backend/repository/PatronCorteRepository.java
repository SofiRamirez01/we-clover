package com.weclover.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;

public interface PatronCorteRepository extends JpaRepository<PatronCorte, Long> {

    List<PatronCorte> findByActivoTrueOrderByNombreAsc();

    List<PatronCorte> findByActivoTrueAndTiposPrenda_IdOrderByNombreAsc(Long idTipoPrenda);

    /** Usado para validar unicidad de numeroInterno por tipo de prenda (no global, ver PatronCorte.numeroInterno). */
    List<PatronCorte> findDistinctByNumeroInternoAndTiposPrenda_IdIn(Integer numeroInterno, Collection<Long> idsTipoPrenda);

    // ---------- Preparación del corte (ver PreparacionCorteService): carga en lote ----------
    // Lo que devuelven no se usa: inicializan las colecciones de esas molderías en la misma
    // transacción. Viven acá porque PatronCorteColor no tiene repositorio propio (se navega
    // siempre desde el agregado PatronCorte, ver PatronCortePosicionPiezaService).

    /** Inicializa PatronCorte.colores (las posiciones de color) de esas molderías. */
    @Query("SELECT DISTINCT pc FROM PatronCorte pc LEFT JOIN FETCH pc.colores WHERE pc IN :patrones")
    List<PatronCorte> cargarPosiciones(@Param("patrones") Collection<PatronCorte> patrones);

    /** Inicializa los pines de cada posición, con su pieza y el tipo de insumo de la pieza. */
    @Query("SELECT DISTINCT c FROM PatronCorteColor c "
        + "LEFT JOIN FETCH c.posicionesPieza pp LEFT JOIN FETCH pp.pieza pz LEFT JOIN FETCH pz.tipoInsumo "
        + "WHERE c.patronCorte IN :patrones")
    List<PatronCorteColor> cargarPines(@Param("patrones") Collection<PatronCorte> patrones);
}
