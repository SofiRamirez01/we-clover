package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoTela;

/**
 * Reparto de los gramos sugeridos entre las filas de un mismo tipo de insumo de un producto
 * (ej. dos colores de Puños y cintura). Cálculo puro y sin estado.
 *
 * - Las filas de un tipo que comparten tela se reparten, en partes iguales y en enteros, los
 *   gramos sugeridos de esa tela (TipoTela.gramosSugerido): 60 con una fila, 30 + 30 con dos,
 *   y si no es exacto el resto va a las primeras (70 entre tres = 24 + 23 + 23).
 * - Una fila del mismo tipo pero de otra tela recibe el sugerido completo de la suya.
 * - Si alguna fila del tipo fue editada a mano, no se toca ninguna fila de ese tipo.
 * - No participan: los insumos libres (sin tipo), los tipos sin material, las telas que no se
 *   compran por peso (ej. Cierre) y las telas sin gramos sugeridos.
 */
public final class RepartoGramosInsumo {

    private RepartoGramosInsumo() {
    }

    /** Reparte `total` en `filas` enteros que suman exactamente `total`; el resto va adelante. */
    public static List<Integer> repartir(int total, int filas) {
        List<Integer> partes = new ArrayList<>();
        int base = total / filas;
        int resto = total % filas;
        for (int i = 0; i < filas; i++) {
            partes.add(base + (i < resto ? 1 : 0));
        }
        return partes;
    }

    public static boolean editadaAMano(ProductoInsumoSecundario insumo) {
        return Boolean.TRUE.equals(insumo.getCantidadEditadaManualmente());
    }

    /**
     * Gramos que le corresponden a cada fila según el reparto, sin mirar si fue editada a mano.
     * Las filas que no participan del reparto no aparecen en el mapa (que es por identidad:
     * dos filas nuevas, todavía sin id, son "iguales" para equals()).
     */
    public static Map<ProductoInsumoSecundario, Integer> gramosEsperados(Producto producto) {
        Map<ProductoInsumoSecundario, Integer> esperados = new IdentityHashMap<>();
        for (List<ProductoInsumoSecundario> grupo : gruposPorTipoYTela(producto).values()) {
            List<Integer> partes = repartir(grupo.get(0).getTipoTela().getGramosSugerido(), grupo.size());
            for (int i = 0; i < grupo.size(); i++) {
                esperados.put(grupo.get(i), partes.get(i));
            }
        }
        return esperados;
    }

    /** Aplica el reparto a las filas de los tipos donde ninguna fue editada a mano. */
    public static void aplicar(Producto producto) {
        Map<ProductoInsumoSecundario, Integer> esperados = gramosEsperados(producto);
        for (ProductoInsumoSecundario insumo : producto.getInsumosSecundarios()) {
            Integer gramos = esperados.get(insumo);
            if (gramos != null && !tipoCongelado(producto, insumo)) {
                insumo.setCantidad(gramos.floatValue());
            }
        }
    }

    /**
     * Filas que comparten tipo de insumo y tela, en el orden en que están en el producto. Solo
     * los grupos que participan del reparto (ver el comentario de la clase).
     */
    public static Map<String, List<ProductoInsumoSecundario>> gruposPorTipoYTela(Producto producto) {
        Map<String, List<ProductoInsumoSecundario>> grupos = new LinkedHashMap<>();
        for (ProductoInsumoSecundario insumo : producto.getInsumosSecundarios()) {
            TipoTela tela = insumo.getTipoTela();
            if (insumo.getTipoInsumo() == null || !insumo.getTipoInsumo().isConsumeMaterial()
                    || tela == null || !tela.isEsPorPeso() || tela.getGramosSugerido() == null) {
                continue;
            }
            String clave = insumo.getTipoInsumo().getCodigo() + "|" + tela.getCodigo();
            grupos.computeIfAbsent(clave, k -> new ArrayList<>()).add(insumo);
        }
        return grupos;
    }

    /** true si alguna fila del mismo tipo de insumo (de cualquier tela) fue editada a mano. */
    private static boolean tipoCongelado(Producto producto, ProductoInsumoSecundario insumo) {
        String codigo = insumo.getTipoInsumo().getCodigo();
        return producto.getInsumosSecundarios().stream()
            .filter(otro -> TipoInsumoCodigos.esDeTipo(otro, codigo))
            .anyMatch(RepartoGramosInsumo::editadaAMano);
    }
}
