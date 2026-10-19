package com.weclover.backend.service;

import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;

/**
 * Códigos estables de tipos_insumo (ver TipoInsumo.codigo) que el código necesita conocer por
 * nombre. Clase de constantes sin dependencias a propósito: la usan cálculos puros como
 * ProductoDisenoValidador y EtapaProduccionAplicabilidad, que no pueden depender de beans.
 */
public final class TipoInsumoCodigos {

    public static final String CAPUCHA = "CAPUCHA";
    public static final String PUNOS_CINTURA = "PUNOS_CINTURA";
    public static final String CIERRE = "CIERRE";
    public static final String ESTAMPADO = "ESTAMPADO";

    private TipoInsumoCodigos() {
    }

    /**
     * true si las filas de este tipo solo pueden usar telas que se compran por peso: son los
     * tipos cuya tela sugerida es por peso (Capucha, Puños y cintura), o sea insumos que se
     * cortan. Cierre (por unidad) y los tipos sin tela sugerida no tienen esta restricción.
     */
    public static boolean soloTelasPorPeso(TipoInsumo tipo) {
        return tipo.getTipoTelaSugerido() != null && tipo.getTipoTelaSugerido().isEsPorPeso();
    }

    /** true si la fila es del tipo con ese código. Una fila libre (sin tipo) nunca lo es. */
    public static boolean esDeTipo(ProductoInsumoSecundario insumo, String codigo) {
        return insumo.getTipoInsumo() != null && codigo.equals(insumo.getTipoInsumo().getCodigo());
    }
}
