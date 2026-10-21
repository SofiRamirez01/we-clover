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
 * @param hayMasLeads  true si quedaron leads sin traer por el límite: hay que ampliarlo o
 *                     repetir con otro rango
 * @param importados   leads importados (o que se importarían, si es simulación)
 * @param salteados    leads salteados (ver motivoSalteo de cada uno)
 * @param leads        detalle de cada lead traído, sin los descartados por falta de Ficha
 */
public record ImportacionKommoResponse(
    boolean simulacion,
    int leadsTraidos,
    int sinFicha,
    boolean hayMasLeads,
    int importados,
    int salteados,
    List<LeadKommoImportacion> leads
) {
}
