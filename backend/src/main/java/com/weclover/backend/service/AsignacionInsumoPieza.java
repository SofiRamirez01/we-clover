package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;

/**
 * Reglas de qué fila de insumo de la ficha cubre cada pin de la moldería de un producto (ver
 * ProductoInsumoPieza). Cálculo puro y sin estado, igual que ProductoDisenoValidador: solo
 * navega el grafo de entidades, para poder usarse desde cualquier servicio sin dependencias.
 *
 * - Los pines de un tipo de insumo son los pines de la moldería del producto cuya Pieza tiene
 *   ese tipo (Pieza.tipoInsumo).
 * - Con una sola fila de ese tipo en el producto, esa fila vale para todos sus pines.
 * - Con dos o más, cada pin debe estar asignado a una fila; el que no lo está queda "sin
 *   asignar". Eso NO vuelve incompleta a la ficha (ver ProductoDisenoValidador, que no mira
 *   esto): es un aviso aparte y un pendiente para el corte.
 */
public final class AsignacionInsumoPieza {

    private AsignacionInsumoPieza() {
    }

    /** Todos los pines de la moldería del producto, por posición de color y orden de alta. */
    public static List<PatronCortePosicionPieza> pines(Producto producto) {
        if (producto.getPatronCorte() == null) {
            return List.of();
        }
        return producto.getPatronCorte().getColores().stream()
            .sorted(Comparator.comparingInt(PatronCorteColor::getOrden))
            .flatMap(color -> color.getPosicionesPieza().stream())
            .toList();
    }

    /** Tipos de insumo que tienen al menos un pin en la moldería, con sus pines. */
    public static Map<TipoInsumo, List<PatronCortePosicionPieza>> pinesPorTipo(Producto producto) {
        Map<String, TipoInsumo> tipoPorCodigo = new LinkedHashMap<>();
        Map<String, List<PatronCortePosicionPieza>> pinesPorCodigo = new LinkedHashMap<>();
        for (PatronCortePosicionPieza pin : pines(producto)) {
            TipoInsumo tipo = pin.getPieza().getTipoInsumo();
            if (tipo == null) {
                continue;
            }
            tipoPorCodigo.putIfAbsent(tipo.getCodigo(), tipo);
            pinesPorCodigo.computeIfAbsent(tipo.getCodigo(), codigo -> new ArrayList<>()).add(pin);
        }
        Map<TipoInsumo, List<PatronCortePosicionPieza>> resultado = new LinkedHashMap<>();
        tipoPorCodigo.forEach((codigo, tipo) -> resultado.put(tipo, pinesPorCodigo.get(codigo)));
        return resultado;
    }

    public static List<ProductoInsumoSecundario> filasDeTipo(Producto producto, TipoInsumo tipo) {
        return producto.getInsumosSecundarios().stream()
            .filter(insumo -> TipoInsumoCodigos.esDeTipo(insumo, tipo.getCodigo()))
            .toList();
    }

    /** true si el producto tiene dos o más filas de ese tipo: recién ahí hay que asignar pines. */
    public static boolean requiereAsignacion(Producto producto, TipoInsumo tipo) {
        return filasDeTipo(producto, tipo).size() >= 2;
    }

    /**
     * Fila explícitamente asignada a un pin. Solo cuenta una asignación coherente: la fila es
     * del mismo tipo de insumo que la pieza del pin. Vacío si el pin no tiene tipo o no está
     * asignado.
     */
    public static Optional<ProductoInsumoSecundario> filaAsignada(Producto producto, PatronCortePosicionPieza pin) {
        TipoInsumo tipo = pin.getPieza().getTipoInsumo();
        if (tipo == null) {
            return Optional.empty();
        }
        return filasDeTipo(producto, tipo).stream()
            .filter(insumo -> insumo.getAsignacionesPieza().stream()
                .anyMatch(asignacion -> asignacion.getPosicionPieza().getId().equals(pin.getId())))
            .findFirst();
    }

    /** Pines que deberían estar asignados (su tipo tiene dos o más filas) y todavía no lo están. */
    public static List<PatronCortePosicionPieza> pinesSinAsignar(Producto producto) {
        List<PatronCortePosicionPieza> sinAsignar = new ArrayList<>();
        pinesPorTipo(producto).forEach((tipo, pinesDelTipo) -> {
            if (!requiereAsignacion(producto, tipo)) {
                return;
            }
            pinesDelTipo.stream()
                .filter(pin -> filaAsignada(producto, pin).isEmpty())
                .forEach(sinAsignar::add);
        });
        return sinAsignar;
    }

    /**
     * Texto con el que se muestra un pin: su etiqueta si la tiene y, si no, el nombre de su
     * pieza — numerado ("Manga 1", "Manga 2") cuando la moldería tiene más de un pin sin
     * etiqueta de esa misma pieza.
     */
    public static String etiqueta(Producto producto, PatronCortePosicionPieza pin) {
        if (pin.getEtiqueta() != null && !pin.getEtiqueta().isBlank()) {
            return pin.getEtiqueta().trim();
        }
        List<PatronCortePosicionPieza> mismaPiezaSinEtiqueta = pines(producto).stream()
            .filter(otro -> otro.getPieza().getId().equals(pin.getPieza().getId()))
            .filter(otro -> otro.getEtiqueta() == null || otro.getEtiqueta().isBlank())
            .toList();
        String nombre = pin.getPieza().getNombre();
        if (mismaPiezaSinEtiqueta.size() <= 1) {
            return nombre;
        }
        for (int i = 0; i < mismaPiezaSinEtiqueta.size(); i++) {
            if (mismaPiezaSinEtiqueta.get(i).getId().equals(pin.getId())) {
                return nombre + " " + (i + 1);
            }
        }
        return nombre;
    }
}
