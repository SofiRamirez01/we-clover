package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.List;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoColor;
import com.weclover.backend.entity.ProductoInsumoPieza;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.TipoTela;

/**
 * Armado de datos compartido por los tests de regresión de insumos secundarios (aplicabilidad
 * de ESTAMPADO, insumos faltantes, cierre y cálculo de compras). Los insumos "conocidos"
 * (capucha, puños y cintura, cierre, estampado) se arman SOLO acá: si cambia la forma en que el
 * sistema los identifica, se ajusta este archivo y las aserciones de los tests no se tocan.
 */
final class InsumosDePrueba {

    static final TipoTela FRIZA = tela(1L, "FRIZA", "Frisa", true, true, null);
    static final TipoTela JERSEY = tela(2L, "JERSEY", "Jersey", true, true, 70);
    static final TipoTela RIBB = tela(5L, "RIBB", "Ribb", true, false, 60);
    static final TipoTela CIERRE = tela(6L, "CIERRE", "Cierre", false, false, null);

    static final TipoInsumo TIPO_CAPUCHA = tipoInsumo(1L, TipoInsumoCodigos.CAPUCHA, "Capucha", true, JERSEY);
    static final TipoInsumo TIPO_PUNOS_CINTURA = tipoInsumo(2L, TipoInsumoCodigos.PUNOS_CINTURA, "Puños y cintura", true, RIBB);
    static final TipoInsumo TIPO_CIERRE = tipoInsumo(3L, TipoInsumoCodigos.CIERRE, "Cierre", true, CIERRE);
    static final TipoInsumo TIPO_ESTAMPADO = tipoInsumo(4L, TipoInsumoCodigos.ESTAMPADO, "Estampado", false, null);

    private InsumosDePrueba() {
    }

    static TipoTela tela(Long id, String codigo, String nombre, boolean esPorPeso, boolean telaCuerpo, Integer gramosSugerido) {
        return TipoTela.builder().id(id).codigo(codigo).nombre(nombre)
            .esPorPeso(esPorPeso).telaCuerpo(telaCuerpo).gramosSugerido(gramosSugerido).build();
    }

    static TipoInsumo tipoInsumo(Long id, String codigo, String nombre, boolean consumeMaterial, TipoTela tipoTelaSugerido) {
        return TipoInsumo.builder().id(id).codigo(codigo).nombre(nombre)
            .consumeMaterial(consumeMaterial).tipoTelaSugerido(tipoTelaSugerido).build();
    }

    static PaletaColores color(Long id, String nombre, TipoTela tipoTela) {
        return PaletaColores.builder().id(id).nombre(nombre).hex("#112233").tipoTela(tipoTela).build();
    }

    static ProductoInsumoSecundario capucha(PaletaColores color, float gramos) {
        return insumo(TIPO_CAPUCHA, "Capucha", JERSEY, color, gramos);
    }

    static ProductoInsumoSecundario punosYCintura(PaletaColores color, float gramos) {
        return insumo(TIPO_PUNOS_CINTURA, "Puños y cintura", RIBB, color, gramos);
    }

    static ProductoInsumoSecundario cierre(PaletaColores color) {
        return insumo(TIPO_CIERRE, "Cierre", CIERRE, color, 1f);
    }

    /** Marca sin consumo de material: sin tela, color ni cantidad. */
    static ProductoInsumoSecundario estampado() {
        return insumo(TIPO_ESTAMPADO, "Estampado", null, null, null);
    }

    /** Insumo cargado a mano con texto libre (ej. "Bolsillo"). */
    static ProductoInsumoSecundario libre(String descripcion, TipoTela tipoTela, PaletaColores color, Float cantidad) {
        return insumo(null, descripcion, tipoTela, color, cantidad);
    }

    private static ProductoInsumoSecundario insumo(TipoInsumo tipoInsumo, String descripcion, TipoTela tipoTela, PaletaColores color, Float cantidad) {
        return ProductoInsumoSecundario.builder()
            .tipoInsumo(tipoInsumo)
            .descripcion(descripcion)
            .tipoTela(tipoTela)
            .color(color)
            .cantidad(cantidad)
            .asignacionesPieza(new ArrayList<>())
            .build();
    }

    /** Producto sin tela, moldería, imagen ni colores: cada test completa lo que necesita. */
    static Producto producto(Long id, String tipoPrenda, int cantidadTotal) {
        return Producto.builder()
            .id(id)
            .tipoPrenda(TipoPrenda.builder().nombre(tipoPrenda).build())
            .cantidadTotal(cantidadTotal)
            .colores(new ArrayList<>())
            .insumosSecundarios(new ArrayList<>())
            .etapas(new ArrayList<>())
            .build();
    }

    /** Producto con tela Friza, imagen y una moldería con una posición por cada gramaje, todas
     *  con su color marcado — o sea, con el "diseño" completo salvo los insumos secundarios. */
    static Producto productoConDisenoCargado(Long id, String tipoPrenda, int cantidadTotal, PaletaColores colorCuerpo, int... gramosPorPosicion) {
        Producto producto = producto(id, tipoPrenda, cantidadTotal);
        producto.setTipoTela(FRIZA);
        producto.setImagenDisenoUrl("/uploads/fichas-tecnicas/diseno.png");

        PatronCorte patronCorte = PatronCorte.builder().id(100L).nombre("Clasica").colores(new ArrayList<>()).build();
        for (int i = 0; i < gramosPorPosicion.length; i++) {
            PatronCorteColor posicion = PatronCorteColor.builder()
                .id(200L + i).patronCorte(patronCorte).orden(i + 1).gramos(gramosPorPosicion[i])
                .posicionesPieza(new ArrayList<>())
                .build();
            patronCorte.getColores().add(posicion);
            producto.getColores().add(ProductoColor.builder()
                .producto(producto).patronCorteColor(posicion).paletaColor(colorCuerpo).build());
        }
        producto.setPatronCorte(patronCorte);
        return producto;
    }

    static Pieza pieza(Long id, String nombre, TipoInsumo tipoInsumo) {
        return Pieza.builder().id(id).nombre(nombre).tipoInsumo(tipoInsumo).build();
    }

    /** Agrega un pin de esa pieza a la posición de color indicada (0 = la primera) de la moldería. */
    static PatronCortePosicionPieza pin(Long id, Producto producto, int indicePosicion, Pieza pieza, String etiqueta) {
        PatronCorteColor posicion = producto.getPatronCorte().getColores().get(indicePosicion);
        PatronCortePosicionPieza pin = PatronCortePosicionPieza.builder()
            .id(id).patronCorteColor(posicion).pieza(pieza).etiqueta(etiqueta).build();
        posicion.getPosicionesPieza().add(pin);
        return pin;
    }

    /** Marca que esa fila de insumo cubre ese pin. */
    static void asignar(ProductoInsumoSecundario fila, PatronCortePosicionPieza pin) {
        fila.getAsignacionesPieza().add(ProductoInsumoPieza.builder()
            .producto(fila.getProducto()).productoInsumo(fila).posicionPieza(pin).build());
    }

    static void agregar(Producto producto, ProductoInsumoSecundario... insumos) {
        for (ProductoInsumoSecundario insumo : List.of(insumos)) {
            insumo.setProducto(producto);
            producto.getInsumosSecundarios().add(insumo);
        }
    }
}
