package com.weclover.backend.service;

import java.util.List;

/**
 * Datos crudos (strings, sin parsear ni validar) de una fila del Excel de exportación de leads
 * de Kommo, extraídos por {@link PedidoImportService}. La validación/parseo y el armado de
 * entidades vive en {@link PedidoImportRowService} — ver doc/pantallas-pendientes.md para el
 * detalle de a qué columna del Excel corresponde cada campo y qué reglas de negocio se
 * acordaron para el mapeo.
 */
record FilaExcelPedido(
    int filaExcel,
    String colegio,
    String localidad,
    String provincia,
    String nivel,
    String contactoPrincipal,
    String telefonoContacto,
    String emailContacto,
    String responsableVendedor,
    String etiquetaEstado,
    String promo,
    String fichaKommo,
    String precioUnitario,
    String presupuesto,
    String buzos,
    String camperas,
    String remeras,
    String chombas,
    String bandera,
    String contrato,
    String pique,
    String tejido,
    String fechaVenta,
    String fechaEntregaPactada,
    String cuotas,
    String responsableCurso,
    List<String> notas
) {
}
