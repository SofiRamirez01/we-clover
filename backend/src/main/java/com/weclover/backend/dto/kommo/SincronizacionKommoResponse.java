package com.weclover.backend.dto.kommo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una sincronización real con Kommo (ver SincronizacionKommo). */
public record SincronizacionKommoResponse(
    Long id,
    LocalDateTime fechaHora,
    Long idUsuario,
    String nombreUsuario,
    LocalDate desde,
    LocalDate hasta,
    List<Long> idsLeads,
    int importados,
    int salteados,
    int sinFicha
) {
}
