package com.weclover.backend.service;

import java.util.List;

import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.Producto;

/**
 * Cálculo puro (sin acceso a repositorios) de qué EtapaProduccion aplican a un Producto
 * no-Bandera (ver 3.1 de la entrega de Producción): CORTE, BORDADO, CONFECCION, APODO y CONTROL
 * siempre aplican; ESTAMPADO solo si el producto tiene un insumo secundario con
 * descripcion="Estampado"; OJAL solo si tipoPrenda es "Chomba". Bandera no participa de este
 * mecanismo en absoluto (ver Producto.estadoBandera) — no se llama a esto para un producto
 * Bandera.
 *
 * Clase estática y sin estado a propósito: la usan tanto ProductoEtapaProduccionService como
 * EstadoPedidoService, y ambos servicios ya se necesitan mutuamente (marcar una etapa dispara
 * el recálculo del estado del pedido) — ponerlo en cualquiera de los dos como método de
 * instancia hubiera creado una dependencia circular entre beans de Spring.
 */
public final class EtapaProduccionAplicabilidad {

    private static final String DESCRIPCION_ESTAMPADO = "Estampado";
    private static final String TIPO_PRENDA_CHOMBA = "Chomba";
    public static final String TIPO_PRENDA_BANDERA = "Bandera";

    private EtapaProduccionAplicabilidad() {
    }

    public static boolean esBandera(Producto producto) {
        return producto.getTipoPrenda() != null
            && TIPO_PRENDA_BANDERA.equalsIgnoreCase(producto.getTipoPrenda().getNombre());
    }

    public static List<EtapaProduccion> etapasAplicables(Producto producto) {
        boolean tieneEstampado = producto.getInsumosSecundarios().stream()
            .anyMatch(insumo -> DESCRIPCION_ESTAMPADO.equals(insumo.getDescripcion()));
        boolean esChomba = producto.getTipoPrenda() != null
            && TIPO_PRENDA_CHOMBA.equalsIgnoreCase(producto.getTipoPrenda().getNombre());

        return List.of(EtapaProduccion.values()).stream()
            .filter(etapa -> switch (etapa) {
                case ESTAMPADO -> tieneEstampado;
                case OJAL -> esChomba;
                default -> true;
            })
            .toList();
    }
}
