package com.weclover.backend.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.repository.TipoTelaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tela con la que se precarga cada producto nuevo según su tipo de prenda, tanto en el alta
 * manual (PedidoService) como en la importada (PedidoImportRowService). Es editable después
 * desde el selector de tela del modal de gotero (ver ModalColoresGotero.tsx /
 * ProductoService.actualizarTipoTela).
 */
@Component
@RequiredArgsConstructor
class TipoTelaPorDefecto {

    /**
     * Código de tela (ver TipoTela.codigo) por nombre de tipo de prenda. TipoTela pasó de enum a
     * entidad, así que acá solo se guarda el código estable y se resuelve contra la base en cada
     * alta.
     */
    private static final Map<String, String> CODIGO_POR_TIPO_PRENDA = Map.of(
        "Buzo", "FRIZA",
        "Remera", "JERSEY",
        "Chomba", "PIQUE",
        "Campera", "FRIZA",
        "Bandera", "SPUM"
    );

    private final TipoTelaRepository tipoTelaRepository;

    /** null si el tipo de prenda no tiene default o si esa fila no está en el catálogo. */
    TipoTela resolver(TipoPrenda tipoPrenda) {
        String codigo = CODIGO_POR_TIPO_PRENDA.get(tipoPrenda.getNombre());
        return codigo != null ? tipoTelaRepository.findByCodigo(codigo).orElse(null) : null;
    }
}
