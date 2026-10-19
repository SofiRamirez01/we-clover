package com.weclover.backend.service;

import java.util.List;
import java.util.Optional;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoColor;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.entity.TipoTela;

/**
 * En qué tela y de qué color se corta la pieza de un pin de la moldería, para un producto
 * puntual (entrada del motor de corte, Módulo 4). Cálculo puro y sin estado, igual que
 * AsignacionInsumoPieza. Nunca asume una tela ni un color: si falta un dato, devuelve el
 * motivo tipado en vez de un valor por defecto.
 *
 * - Pieza sin tipo de insumo (cuerpo): tela del producto y color marcado en la posición de
 *   color donde está el pin.
 * - Pieza con tipo de insumo: la fila de ese tipo en la ficha — la única si hay una sola, o la
 *   asignada al pin si hay varias.
 */
public final class ResolucionTelaPin {

    /** Por qué no se pudo determinar la tela o el color de un pin. */
    public enum Motivo {
        /** La pieza es de un tipo de insumo y el producto no tiene ninguna fila de ese tipo. */
        FALTA_INSUMO,
        /** El producto tiene varias filas de ese tipo y el pin no está asignado a ninguna. */
        SIN_ASIGNAR,
        /** La fila de insumo que le corresponde no tiene tela o no tiene color. */
        INSUMO_INCOMPLETO,
        /** La tela resuelta no se compra por peso (ej. Cierre): no es una tela que se corte. */
        TELA_NO_CORTABLE,
        /** Pieza del cuerpo y el producto no tiene tela, o esa posición no tiene color marcado. */
        CUERPO_INCOMPLETO
    }

    /**
     * Resultado de resolver un pin: `tela` y `color` si se pudo (motivo null), o `motivo` si no
     * (tela y color null). `insumo` es la fila de la ficha de la que salieron, o null para una
     * pieza del cuerpo.
     */
    public record Resultado(TipoTela tela, PaletaColores color, ProductoInsumoSecundario insumo, Motivo motivo) {

        public boolean resuelto() {
            return motivo == null;
        }

        static Resultado de(TipoTela tela, PaletaColores color, ProductoInsumoSecundario insumo) {
            return new Resultado(tela, color, insumo, null);
        }

        static Resultado pendiente(Motivo motivo, ProductoInsumoSecundario insumo) {
            return new Resultado(null, null, insumo, motivo);
        }
    }

    private ResolucionTelaPin() {
    }

    public static Resultado resolver(Producto producto, PatronCortePosicionPieza pin) {
        TipoInsumo tipo = pin.getPieza().getTipoInsumo();
        if (tipo == null) {
            return resolverCuerpo(producto, pin);
        }

        List<ProductoInsumoSecundario> filas = AsignacionInsumoPieza.filasDeTipo(producto, tipo);
        if (filas.isEmpty()) {
            return Resultado.pendiente(Motivo.FALTA_INSUMO, null);
        }

        ProductoInsumoSecundario fila;
        if (filas.size() == 1) {
            fila = filas.get(0);
        } else {
            Optional<ProductoInsumoSecundario> asignada = AsignacionInsumoPieza.filaAsignada(producto, pin);
            if (asignada.isEmpty()) {
                return Resultado.pendiente(Motivo.SIN_ASIGNAR, null);
            }
            fila = asignada.get();
        }

        if (fila.getTipoTela() == null || fila.getColor() == null) {
            return Resultado.pendiente(Motivo.INSUMO_INCOMPLETO, fila);
        }
        if (!fila.getTipoTela().isEsPorPeso()) {
            return Resultado.pendiente(Motivo.TELA_NO_CORTABLE, fila);
        }
        return Resultado.de(fila.getTipoTela(), fila.getColor(), fila);
    }

    private static Resultado resolverCuerpo(Producto producto, PatronCortePosicionPieza pin) {
        Optional<ProductoColor> colorDeLaPosicion = producto.getColores().stream()
            .filter(color -> color.getPatronCorteColor().getId().equals(pin.getPatronCorteColor().getId()))
            .findFirst();
        if (producto.getTipoTela() == null || colorDeLaPosicion.isEmpty()) {
            return Resultado.pendiente(Motivo.CUERPO_INCOMPLETO, null);
        }
        if (!producto.getTipoTela().isEsPorPeso()) {
            return Resultado.pendiente(Motivo.TELA_NO_CORTABLE, null);
        }
        return Resultado.de(producto.getTipoTela(), colorDeLaPosicion.get().getPaletaColor(), null);
    }
}
