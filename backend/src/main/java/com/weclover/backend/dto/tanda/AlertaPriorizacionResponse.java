package com.weclover.backend.dto.tanda;

/**
 * Aviso para quien prioriza. Se calcula en cada consulta, no se persiste. Hoy hay un solo tipo;
 * `tipo` queda como discriminador para sumar otros (ver doc/pantallas-pendientes.md: alerta por
 * cambio de % de pago).
 */
public record AlertaPriorizacionResponse(
    Tipo tipo,
    Long idPedido,
    String codigoInterno,
    String colegio,
    String curso
) {

    public enum Tipo {
        /** El pedido ya está LISTO_PARA_PRODUCCION y todavía no tiene tanda. */
        LISTO_SIN_TANDA
    }
}
