package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.repository.ProductoInsumoSecundarioRepository;
import com.weclover.backend.repository.TipoInsumoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Migración de arranque (ver DataInitializer): asocia a su TipoInsumo las filas de insumo
 * cargadas cuando el insumo se identificaba solo por el texto de `descripcion`.
 */
@Service
@RequiredArgsConstructor
public class TipoInsumoMigracionService {

    /**
     * Resultado de una corrida, para loguearlo: cuántas filas se asociaron a cada tipo (por
     * código) y cuántas quedaron como insumo libre (por descripción, tal cual está guardada).
     */
    public record Reporte(Map<String, Integer> asociadasPorTipo, Map<String, Integer> libresPorDescripcion) {

        public int totalAsociadas() {
            return asociadasPorTipo.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int totalLibres() {
            return libresPorDescripcion.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private final ProductoInsumoSecundarioRepository productoInsumoSecundarioRepository;
    private final TipoInsumoRepository tipoInsumoRepository;

    /**
     * Asocia cada fila sin tipo a un TipoInsumo solo si su descripción coincide exactamente con
     * el nombre de ese tipo, sin distinguir mayúsculas ni espacios. Lo que no coincide queda
     * como insumo libre: no se adivina por parecido. Idempotente: solo mira filas con
     * tipoInsumo en null y no toca ningún otro dato de la fila.
     */
    @Transactional
    public Reporte asociarInsumosPorDescripcion() {
        Map<String, TipoInsumo> tiposPorNombre = tipoInsumoRepository.findAll().stream()
            .collect(Collectors.toMap(tipo -> normalizar(tipo.getNombre()), Function.identity()));

        Map<String, Integer> asociadasPorTipo = new TreeMap<>();
        Map<String, Integer> libresPorDescripcion = new TreeMap<>();

        for (ProductoInsumoSecundario insumo : productoInsumoSecundarioRepository.findByTipoInsumoIsNull()) {
            TipoInsumo tipo = tiposPorNombre.get(normalizar(insumo.getDescripcion()));
            if (tipo == null) {
                libresPorDescripcion.merge(insumo.getDescripcion(), 1, Integer::sum);
                continue;
            }
            insumo.setTipoInsumo(tipo);
            productoInsumoSecundarioRepository.save(insumo);
            asociadasPorTipo.merge(tipo.getCodigo(), 1, Integer::sum);
        }

        return new Reporte(asociadasPorTipo, libresPorDescripcion);
    }

    /**
     * Resultado de evaluar el indicador de edición manual: cuántas filas quedaron marcadas como
     * editadas a mano, cuántas como sugeridas, y los grupos (producto + tipo + tela) con varias
     * filas cuya suma de gramos no coincide con el sugerido de la tela.
     */
    public record ReporteGramos(int marcadasEditadas, int marcadasSugeridas, List<String> gruposConSumaDistinta) {

        public int totalEvaluadas() {
            return marcadasEditadas + marcadasSugeridas;
        }
    }

    /**
     * Completa `cantidadEditadaManualmente` en las filas anteriores a esa columna (null): una
     * fila queda como editada a mano si su cantidad difiere de la que le daría el reparto
     * (gramos sugeridos de su tela ÷ filas del grupo, ver RepartoGramosInsumo) — incluidas las
     * filas únicas que no coinciden con el sugerido. Así el reparto automático no pisa valores
     * que alguien eligió. Solo se escribe el indicador: los gramos no se tocan.
     *
     * Idempotente: solo mira filas con el indicador en null, y las deja todas resueltas. Se
     * debe llamar después de asociarInsumosPorDescripcion (el reparto es por tipo de insumo).
     */
    @Transactional
    public ReporteGramos marcarCantidadesEditadasAMano() {
        List<ProductoInsumoSecundario> sinEvaluar = productoInsumoSecundarioRepository.findByCantidadEditadaManualmenteIsNull();

        Map<Long, Producto> productos = new TreeMap<>();
        sinEvaluar.forEach(insumo -> productos.putIfAbsent(insumo.getProducto().getId(), insumo.getProducto()));

        int editadas = 0;
        int sugeridas = 0;
        List<String> gruposConSumaDistinta = new ArrayList<>();

        for (Producto producto : productos.values()) {
            Map<ProductoInsumoSecundario, Integer> esperados = RepartoGramosInsumo.gramosEsperados(producto);
            for (ProductoInsumoSecundario insumo : producto.getInsumosSecundarios()) {
                if (insumo.getCantidadEditadaManualmente() != null) {
                    continue;
                }
                Integer esperado = esperados.get(insumo);
                boolean editada = esperado != null && (insumo.getCantidad() == null || insumo.getCantidad() != esperado.floatValue());
                insumo.setCantidadEditadaManualmente(editada);
                productoInsumoSecundarioRepository.save(insumo);
                if (editada) {
                    editadas++;
                } else {
                    sugeridas++;
                }
            }

            for (List<ProductoInsumoSecundario> grupo : RepartoGramosInsumo.gruposPorTipoYTela(producto).values()) {
                if (grupo.size() < 2) {
                    continue;
                }
                double suma = grupo.stream().mapToDouble(insumo -> insumo.getCantidad() != null ? insumo.getCantidad() : 0).sum();
                int sugerido = grupo.get(0).getTipoTela().getGramosSugerido();
                if (Math.abs(suma - sugerido) > 0.001) {
                    gruposConSumaDistinta.add("producto #" + producto.getId() + " " + grupo.get(0).getTipoInsumo().getCodigo()
                        + "/" + grupo.get(0).getTipoTela().getCodigo() + ": " + grupo.size() + " filas suman "
                        + String.format(Locale.ROOT, "%.2f", suma) + " g (sugerido " + sugerido + " g)");
                }
            }
        }

        return new ReporteGramos(editadas, sugeridas, gruposConSumaDistinta);
    }

    /** Minúsculas y sin ningún espacio: "  puños Y  cintura " equivale a "Puños y cintura". */
    static String normalizar(String texto) {
        return texto == null ? "" : texto.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
