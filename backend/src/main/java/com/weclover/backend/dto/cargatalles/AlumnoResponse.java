package com.weclover.backend.dto.cargatalles;

import java.util.List;

public record AlumnoResponse(
    Long id,
    String nombreAlumno,
    int orden,
    List<ComboResponse> combos
) {
}
