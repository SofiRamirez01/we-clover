package com.weclover.backend.dto.pedido;

import java.util.List;

/**
 * Resultado de POST /api/pedidos/importar-kommo. Con simular=true informa qué pasaría con cada
 * lead sin guardar nada; con simular=false, qué pasó. Las dos usan las mismas reglas, así que
 * los motivos de salteo son los mismos.
 *
 * @param simulacion   true si no se guardó nada
 * @param leadsTraidos cantidad de leads que devolvió Kommo (cuenta para el límite pedido)
 * @param sinFicha     leads descartados antes de mapear por no tener Ficha (no son errores)
 * @param hayMasLeads  true si el pedido trae más leads que maximoLeads: la importación real
 *                     se rechaza (hay que achicar el rango o pedir menos ids)
 * @param maximoLeads  tope de leads por llamada que se aplicó
 * @param importados   leads importados (o que se importarían, si es simulación)
 * @param salteados    leads salteados (ver motivoSalteo de cada uno), incluidos los ya importados
 * @param yaImportados de los salteados, los que ya se habían importado antes
 * @param leads        detalle de cada lead traído, sin los descartados por falta de Ficha
 */
public record ImportacionKommoResponse(
    boolean simulacion,
    int leadsTraidos,
    int sinFicha,
    boolean hayMasLeads,
    int maximoLeads,
    int importados,
    int salteados,
    int yaImportados,
    List<LeadKommoImportacion> leads
) {
}
