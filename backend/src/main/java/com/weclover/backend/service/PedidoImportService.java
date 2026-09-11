package com.weclover.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.pedido.FilaImportacionSaltada;
import com.weclover.backend.dto.pedido.ImportacionPedidosExcelResponse;
import com.weclover.backend.dto.pedido.PedidoImportadoResumen;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.exception.BusinessRuleException;

import lombok.RequiredArgsConstructor;

/**
 * Importa pedidos desde el Excel de exportación de leads de Kommo (ver
 * doc/pantallas-pendientes.md para el detalle del mapeo de columnas acordado con el negocio).
 * Cada fila se procesa en su propia transacción (ver PedidoImportRowService): si una fila tiene
 * datos inválidos o incompletos, se saltea y se reporta con el motivo — no se inventan datos que
 * el Excel no trae, y el resto de las filas se importa igual.
 */
@Service
@RequiredArgsConstructor
public class PedidoImportService {

    /**
     * Posición (0-based) de cada columna en la plantilla de exportación de Kommo. La columna
     * "Responsable" aparece dos veces con el mismo encabezado (vendedor y responsable de curso
     * alumno/adulto) — se identifican por posición, no por nombre, porque el nombre es ambiguo.
     */
    private static final int COL_RESPONSABLE_VENDEDOR = 5;
    private static final int COL_ETIQUETAS_LEAD = 13;
    private static final int COL_PRESUPUESTO = 8;
    private static final int COL_PRECIO_UNITARIO = 17;
    private static final int COL_PROMO = 18;
    private static final int COL_FICHA = 25;
    private static final int COL_COLEGIO = 19;
    private static final int COL_PROVINCIA = 20;
    private static final int COL_LOCALIDAD = 21;
    private static final int COL_NIVEL = 22;
    private static final int COL_RESPONSABLE_CURSO = 23;
    private static final int COL_BUZOS = 33;
    private static final int COL_CAMPERAS = 34;
    private static final int COL_REMERAS = 35;
    private static final int COL_CHOMBAS = 36;
    private static final int COL_BANDERA = 37;
    private static final int COL_CONTRATO = 38;
    private static final int COL_PIQUE = 40;
    private static final int COL_TEJIDO = 41;
    private static final int COL_FECHA_VENTA = 44;
    private static final int COL_FECHA_ENTREGA_PACTADA = 45;
    private static final int COL_CUOTAS = 46;
    private static final int COL_CONTACTO_PRINCIPAL = 3;
    private static final int COL_CORREO_CONTACTO = 61;
    private static final int COL_EMAIL_PRIV_CONTACTO = 62;
    private static final int COL_OTRO_EMAIL_CONTACTO = 63;
    private static final int COL_TELEFONO_OFICINA_CONTACTO = 64;
    private static final int[] COLS_NOTAS = { 72, 73, 74, 75, 76 };

    private final PedidoImportRowService pedidoImportRowService;

    public ImportacionPedidosExcelResponse importarDesdeExcel(MultipartFile archivo) {
        List<PedidoImportadoResumen> importados = new ArrayList<>();
        List<FilaImportacionSaltada> salteadas = new ArrayList<>();

        try (InputStream in = archivo.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet hoja = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int totalFilas = 0;

            for (int i = hoja.getFirstRowNum() + 1; i <= hoja.getLastRowNum(); i++) {
                Row row = hoja.getRow(i);
                if (row == null || esFilaVacia(row, formatter)) {
                    continue;
                }
                totalFilas++;
                int numeroFilaExcel = i + 1;
                String colegio = valor(row, COL_COLEGIO, formatter);
                try {
                    FilaExcelPedido fila = parsearFila(row, formatter, numeroFilaExcel);
                    Pedido pedido = pedidoImportRowService.importarFila(fila);
                    importados.add(new PedidoImportadoResumen(
                        numeroFilaExcel, pedido.getId(), pedido.getCodigoInterno(), pedido.getColegio().getNombre()));
                } catch (RuntimeException e) {
                    salteadas.add(new FilaImportacionSaltada(numeroFilaExcel, colegio, e.getMessage()));
                }
            }

            return new ImportacionPedidosExcelResponse(totalFilas, importados.size(), importados, salteadas);
        } catch (IOException e) {
            throw new BusinessRuleException("No se pudo leer el archivo Excel: " + e.getMessage());
        }
    }

    private boolean esFilaVacia(Row row, DataFormatter formatter) {
        return valor(row, COL_COLEGIO, formatter).isBlank();
    }

    private FilaExcelPedido parsearFila(Row row, DataFormatter formatter, int numeroFilaExcel) {
        List<String> notas = new ArrayList<>();
        for (int col : COLS_NOTAS) {
            notas.add(valor(row, col, formatter));
        }
        return new FilaExcelPedido(
            numeroFilaExcel,
            valor(row, COL_COLEGIO, formatter),
            valor(row, COL_LOCALIDAD, formatter),
            valor(row, COL_PROVINCIA, formatter),
            valor(row, COL_NIVEL, formatter),
            valor(row, COL_CONTACTO_PRINCIPAL, formatter),
            valor(row, COL_TELEFONO_OFICINA_CONTACTO, formatter),
            primerNoVacio(
                valor(row, COL_CORREO_CONTACTO, formatter),
                valor(row, COL_EMAIL_PRIV_CONTACTO, formatter),
                valor(row, COL_OTRO_EMAIL_CONTACTO, formatter)),
            valor(row, COL_RESPONSABLE_VENDEDOR, formatter),
            valor(row, COL_ETIQUETAS_LEAD, formatter),
            valor(row, COL_PROMO, formatter),
            valor(row, COL_FICHA, formatter),
            valor(row, COL_PRECIO_UNITARIO, formatter),
            valor(row, COL_PRESUPUESTO, formatter),
            valor(row, COL_BUZOS, formatter),
            valor(row, COL_CAMPERAS, formatter),
            valor(row, COL_REMERAS, formatter),
            valor(row, COL_CHOMBAS, formatter),
            valor(row, COL_BANDERA, formatter),
            valor(row, COL_CONTRATO, formatter),
            valor(row, COL_PIQUE, formatter),
            valor(row, COL_TEJIDO, formatter),
            valor(row, COL_FECHA_VENTA, formatter),
            valor(row, COL_FECHA_ENTREGA_PACTADA, formatter),
            valor(row, COL_CUOTAS, formatter),
            valor(row, COL_RESPONSABLE_CURSO, formatter),
            notas
        );
    }

    private String valor(Row row, int columna, DataFormatter formatter) {
        var cell = row.getCell(columna);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String primerNoVacio(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
