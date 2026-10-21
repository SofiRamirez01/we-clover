package com.weclover.backend.dto.pedido;

import java.time.LocalDate;

import org.springframework.data.domain.Sort;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.OrigenPedido;

/**
 * Filtros del listado paginado de Base Ventas (ver PedidoListadoService), tal cual llegan por
 * query params: todos opcionales. Los usan las dos pestañas — Pedidos (por defecto
 * soloActivos=true) e Histórico (por defecto la promoción del año actual) — pero los valores
 * por defecto los decide el frontend, acá null significa "sin filtrar".
 *
 * @param buscar        texto libre sobre Nº de ficha, colegio, localidad o vendedor
 * @param promocion     año de la promoción: los 4 primeros dígitos del Nº de ficha (2026-05 → 2026)
 * @param soloActivos   true = sin ENTREGADO ni CANCELADO
 * @param idPatronCorte moldería; va siempre junto con idTipoPrenda ("moldería – tipo de prenda")
 * @param desde         fecha de venta desde (inclusive)
 * @param hasta         fecha de venta hasta (inclusive)
 * @param pagina        desde 0
 * @param tamanio       pedidos por página (por defecto 50, máximo 100)
 * @param orden         columna por la que se ordena; null = del pedido más nuevo al más viejo
 * @param direccion     ASC (de menor a mayor, por defecto) o DESC
 * @param origen        cómo se dio de alta el pedido (manual, Excel o Kommo)
 */
public record FiltrosListadoPedidos(
    String buscar,
    Integer promocion,
    EstadoPedido estado,
    Boolean soloActivos,
    Long idPatronCorte,
    Long idTipoPrenda,
    LocalDate desde,
    LocalDate hasta,
    Integer pagina,
    Integer tamanio,
    CampoOrdenPedidos orden,
    Sort.Direction direccion,
    OrigenPedido origen
) {
}
