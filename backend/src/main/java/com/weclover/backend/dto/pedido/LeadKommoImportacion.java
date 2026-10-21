package com.weclover.backend.dto.pedido;

import com.weclover.backend.entity.EstadoPedido;

/**
 * Un lead de Kommo en la importación (o su simulación).
 *
 * @param idLead                 id del lead en Kommo
 * @param colegio                campo Colegio del lead
 * @param vendedorKommo          campo Vendedor del lead, tal cual
 * @param vendedorAsignado       nombre del usuario vinculado a ese vendedor (null si no hay o la
 *                               fila se saltea antes)
 * @param estado                 EstadoPedido resuelto desde el status_id (null si no es importable)
 * @param cantidades             cantidades por prenda tal cual vienen en el lead
 * @param fichaKommo             campo Ficha del lead (ej. "241-27")
 * @param codigoInternoPropuesto Nº de ficha que se generaría: año de Promo + número de la Ficha
 *                               antes del guion (null si Promo o Ficha no se pueden interpretar)
 * @param idPedido               pedido creado; null en la simulación o si se salteó
 * @param motivoSalteo           por qué no se importa; null si se importa
 * @param yaImportado            el salteo es porque ya se había importado (mismo lead o Nº de
 *                               ficha): no hay nada que corregir
 */
public record LeadKommoImportacion(
    Long idLead,
    String colegio,
    String vendedorKommo,
    String vendedorAsignado,
    EstadoPedido estado,
    CantidadesPrenda cantidades,
    String fichaKommo,
    String codigoInternoPropuesto,
    Long idPedido,
    String motivoSalteo,
    boolean yaImportado
) {

    /** null = el lead no trae el campo o no es un número. */
    public record CantidadesPrenda(
        Integer buzos,
        Integer camperas,
        Integer remeras,
        Integer chombas,
        boolean bandera
    ) {
    }
}
