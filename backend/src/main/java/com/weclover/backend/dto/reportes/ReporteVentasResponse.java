package com.weclover.backend.dto.reportes;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/reportes/ventas. "Vendido" = pedido desde SENADO en adelante (sin CANCELADO), por
 * Pedido.fechaVenta; Bandera no cuenta en ningún lado.
 *
 * pedidosSinDesglose: pedidos del período sin precio por prenda (solo un total del pedido).
 * - Sin filtro de tipo de prenda, su total SÍ está en montoTotal/porMes, pero no se puede
 *   repartir en porTipoPrenda (ese gráfico queda incompleto si pedidosSinDesglose > 0).
 * - Con filtro de tipo de prenda, sus unidades cuentan pero su monto no: montoIncompleto=true,
 *   y precioPromedioUnidad se calcula solo sobre las unidades que sí tienen precio.
 */
public record ReporteVentasResponse(
    LocalDate desde,
    LocalDate hasta,
    Long idTipoPrenda,

    long unidadesTotales,
    double montoTotal,
    double precioPromedioUnidad,
    long cantidadPedidos,

    boolean montoIncompleto,
    long pedidosSinDesglose,

    List<VentasPorTipoPrendaResponse> porTipoPrenda,
    List<VentasPorMesResponse> porMes,

    VentasPresupuestadosResponse presupuestados
) {
}
