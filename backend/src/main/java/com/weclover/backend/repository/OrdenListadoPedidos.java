package com.weclover.backend.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import com.weclover.backend.dto.pedido.CampoOrdenPedidos;

/**
 * Traduce una columna del listado de Base Ventas a las expresiones JPQL de su ORDER BY sobre
 * el alias `p` de PedidoRepository.buscarListado. Las columnas calculadas (unidades, precio,
 * total, % de pago) repiten acá las mismas fórmulas que EstadoPedidoService.calcularPrecioTotalPedido
 * / calcularPorcentajePagado y PedidoService.construirRespuesta, para que el orden sea el de los
 * valores que se muestran. Son expresiones fijas de esta clase (nunca texto del usuario), por
 * eso es seguro usar JpaSort.unsafe.
 */
public final class OrdenListadoPedidos {

    private static final String PRENDAS_HABILITADAS =
        "FROM Producto pr WHERE pr.pedido = p AND pr.habilitado = true";

    private static final String SUMA_SUBTOTALES =
        "(SELECT COALESCE(SUM(pr.cantidadTotal * pr.costo), 0) " + PRENDAS_HABILITADAS + ")";

    private static final String SUMA_COSTOS =
        "(SELECT COALESCE(SUM(pr.costo), 0) " + PRENDAS_HABILITADAS + ")";

    /** precioTotal: suma de cantidad × costo, o el monto importado si las prendas no tienen costo. */
    private static final String TOTAL =
        "CASE WHEN " + SUMA_SUBTOTALES + " > 0 THEN " + SUMA_SUBTOTALES
            + " ELSE COALESCE(p.montoReferenciaImportado, 0) END";

    private static final String PRECIO_UNITARIO =
        "CASE WHEN " + SUMA_COSTOS + " > 0 THEN " + SUMA_COSTOS
            + " ELSE COALESCE(p.precioUnitarioReferenciaImportado, 0) END";

    private static final String PORCENTAJE_PAGO =
        "CASE WHEN " + TOTAL + " > 0 THEN p.pagoInicial * 100.0 / (" + TOTAL + ") ELSE 0 END";

    /** Entre paréntesis: sin ellos Spring Data la toma por un nombre de campo y le antepone el
     *  alias ("p.CASE ..."). */
    private static final String ESTADO_EN_CICLO = "(CASE p.estadoActual "
        + "WHEN com.weclover.backend.entity.EstadoPedido.PRESUPUESTADO THEN 0 "
        + "WHEN com.weclover.backend.entity.EstadoPedido.SENADO THEN 1 "
        + "WHEN com.weclover.backend.entity.EstadoPedido.LISTO_PARA_PRODUCCION THEN 2 "
        + "WHEN com.weclover.backend.entity.EstadoPedido.EN_PRODUCCION THEN 3 "
        + "WHEN com.weclover.backend.entity.EstadoPedido.TERMINADO THEN 4 "
        + "WHEN com.weclover.backend.entity.EstadoPedido.ENTREGADO THEN 5 "
        + "ELSE 6 END)";

    private static final Map<CampoOrdenPedidos, List<String>> EXPRESIONES = Map.ofEntries(
        // "AAAA-N": promoción, después el número por largo y texto (2026-99 antes que 2026-100).
        Map.entry(CampoOrdenPedidos.FICHA, List.of(
            "SUBSTRING(p.codigoInterno, 1, 4)", "LENGTH(p.codigoInterno)", "p.codigoInterno")),
        Map.entry(CampoOrdenPedidos.FECHA_VENTA, List.of("p.fechaVenta")),
        Map.entry(CampoOrdenPedidos.FECHA_ENTREGA, List.of("p.fechaEstimadaEntrega")),
        Map.entry(CampoOrdenPedidos.COLEGIO, List.of("p.colegio.nombre")),
        Map.entry(CampoOrdenPedidos.LOCALIDAD, List.of("p.colegio.localidad")),
        Map.entry(CampoOrdenPedidos.CURSO, List.of("p.curso")),
        Map.entry(CampoOrdenPedidos.VENDEDOR, List.of("p.creadoPor.nombre")),
        Map.entry(CampoOrdenPedidos.UNIDADES_BUZO_CAMPERA, List.of(unidadesDe("'Buzo', 'Campera'"))),
        Map.entry(CampoOrdenPedidos.UNIDADES_REMERA, List.of(unidadesDe("'Remera'"))),
        Map.entry(CampoOrdenPedidos.UNIDADES_CHOMBA, List.of(unidadesDe("'Chomba'"))),
        Map.entry(CampoOrdenPedidos.PRECIO_UNITARIO, List.of(PRECIO_UNITARIO)),
        Map.entry(CampoOrdenPedidos.TOTAL, List.of(TOTAL)),
        Map.entry(CampoOrdenPedidos.PORCENTAJE_PAGO, List.of(PORCENTAJE_PAGO)),
        Map.entry(CampoOrdenPedidos.ESTADO, List.of(ESTADO_EN_CICLO))
    );

    private OrdenListadoPedidos() {
    }

    /**
     * Sin campo: del pedido más nuevo al más viejo. Con campo: esa columna en la dirección
     * pedida, y el id como desempate para que el orden sea estable entre páginas.
     */
    public static Sort de(CampoOrdenPedidos campo, Sort.Direction direccion) {
        if (campo == null) {
            return Sort.by(Sort.Direction.DESC, "fechaCreacion").and(Sort.by(Sort.Direction.DESC, "id"));
        }
        Sort.Direction sentido = direccion != null ? direccion : Sort.Direction.ASC;
        return JpaSort.unsafe(sentido, EXPRESIONES.get(campo)).and(Sort.by(sentido, "id"));
    }

    private static String unidadesDe(String tiposPrenda) {
        return "(SELECT COALESCE(SUM(pr.cantidadTotal), 0) " + PRENDAS_HABILITADAS
            + " AND pr.tipoPrenda.nombre IN (" + tiposPrenda + "))";
    }
}
