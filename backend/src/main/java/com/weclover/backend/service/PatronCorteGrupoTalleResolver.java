package com.weclover.backend.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.exception.BusinessRuleException;

/**
 * PatronCorte no tiene su propio campo grupoTalle: se deriva de sus tiposPrenda (deberían
 * coincidir todos entre sí — ver GrupoTalle/TipoPrenda). Usado tanto para validar que una Pieza
 * asignada a un pin sea del grupo correcto (PatronCortePosicionPiezaService) como para exponer
 * ese grupo en PatronCorteResponse, así el frontend sabe qué Piezas puede ofrecer en el picker.
 */
final class PatronCorteGrupoTalleResolver {

    private PatronCorteGrupoTalleResolver() {
    }

    private static List<GrupoTalle> gruposDistintos(PatronCorte patronCorte) {
        return patronCorte.getTiposPrenda().stream()
            .map(TipoPrenda::getGrupoTalle)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    /** Vacío si el patrón no tiene grupo de talle (ej. solo tipos de prenda como Bandera) o si
     * sus tipos de prenda tienen grupos distintos entre sí — no lanza, para uso en lecturas. */
    static Optional<GrupoTalle> resolverSiExiste(PatronCorte patronCorte) {
        List<GrupoTalle> grupos = gruposDistintos(patronCorte);
        return grupos.size() == 1 ? Optional.of(grupos.get(0)) : Optional.empty();
    }

    /** Para validar una asignación de Pieza: si no hay un único grupo de talle, no tiene
     * sentido seguir (no se sabe contra qué comparar la Pieza elegida). */
    static GrupoTalle resolverOLanzar(PatronCorte patronCorte) {
        List<GrupoTalle> grupos = gruposDistintos(patronCorte);
        if (grupos.isEmpty()) {
            throw new BusinessRuleException("Esta moldería no tiene definido un grupo de talle: no se le pueden asignar piezas");
        }
        if (grupos.size() > 1) {
            throw new BusinessRuleException(
                "Esta moldería tiene tipos de prenda con distintos grupos de talle: no se puede determinar cuál usar");
        }
        return grupos.get(0);
    }
}
