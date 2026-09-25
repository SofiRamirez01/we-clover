package com.weclover.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.weclover.backend.entity.Producto;

/**
 * Elegibilidad de "diseño completo" de un Producto (ver motivoDisenoIncompleto): usada tanto
 * por PlanificacionCompraService (Fase 3) como por EstadoPedidoService (condición de
 * LISTO_PARA_PRODUCCION). Cálculo puro y sin estado a propósito, igual que
 * EtapaProduccionAplicabilidad: EstadoPedidoService no puede depender de ProductoService (ver
 * el comentario de esa clase) sin crear una dependencia circular de beans.
 */
public final class ProductoDisenoValidador {

    /** Tipo de tela + descripción sugeridos para un insumo secundario automático (ver
     *  ProductoService.sugerirInsumoSecundario, que reusa este mismo mapa). */
    public record InsumoSugerido(String codigoTipoTela, String descripcion) {
    }

    public static final Map<String, List<InsumoSugerido>> CODIGOS_INSUMOS_SUGERIDOS_POR_PRENDA = Map.of(
        "Buzo", List.of(new InsumoSugerido("JERSEY", "Capucha"), new InsumoSugerido("RIBB", "Puños y cintura")),
        "Campera", List.of(
            new InsumoSugerido("CIERRE", "Cierre"),
            new InsumoSugerido("JERSEY", "Capucha"),
            new InsumoSugerido("RIBB", "Puños y cintura")
        )
    );

    private ProductoDisenoValidador() {
    }

    /**
     * Vacío si el "diseño" está completo, o el motivo (primero que falle, no la lista
     * completa) si falta algo. Chequea, en orden: tela, moldería, imagen de diseño (agregado
     * en esta entrega — antes no se chequeaba), colores por posición, e insumos secundarios
     * sugeridos según el tipo de prenda (ya se chequeaba desde antes).
     */
    public static Optional<String> motivoDisenoIncompleto(Producto producto) {
        if (producto.getTipoTela() == null) {
            return Optional.of("Falta asignar la tela de esta prenda");
        }
        if (producto.getPatronCorte() == null) {
            return Optional.of("Falta asignar la moldería (patrón de corte)");
        }
        if (producto.getImagenDisenoUrl() == null) {
            return Optional.of("Falta cargar la imagen de diseño");
        }

        int posiciones = producto.getPatronCorte().getColores().size();
        if (producto.getColores().size() != posiciones) {
            return Optional.of(
                "Faltan colores por marcar (" + producto.getColores().size() + " de " + posiciones + " posiciones)");
        }

        if (producto.getTipoPrenda() != null) {
            List<String> faltantes = CODIGOS_INSUMOS_SUGERIDOS_POR_PRENDA
                .getOrDefault(producto.getTipoPrenda().getNombre(), List.of())
                .stream()
                .map(InsumoSugerido::descripcion)
                .filter(descripcion -> producto.getInsumosSecundarios().stream()
                    .noneMatch(insumo -> descripcion.equals(insumo.getDescripcion())))
                .toList();

            if (!faltantes.isEmpty()) {
                return Optional.of("Faltan insumos secundarios: " + String.join(", ", faltantes));
            }
        }

        return Optional.empty();
    }
}
