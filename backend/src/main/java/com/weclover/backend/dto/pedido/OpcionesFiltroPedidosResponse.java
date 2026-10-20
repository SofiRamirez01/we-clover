package com.weclover.backend.dto.pedido;

import java.util.List;

/**
 * Valores posibles de los filtros de Base Ventas, sacados de los pedidos existentes (los que
 * el usuario puede ver: un vendedor solo los suyos).
 *
 * @param promociones de la más nueva a la más vieja
 * @param molderias   combinaciones moldería – tipo de prenda que aparecen en algún pedido; el
 *                    frontend también las usa para mostrar el nombre de la moldería de cada
 *                    prenda (ProductoResponse solo trae idPatronCorte)
 */
public record OpcionesFiltroPedidosResponse(
    List<Integer> promociones,
    List<OpcionMolderiaResponse> molderias
) {
}
