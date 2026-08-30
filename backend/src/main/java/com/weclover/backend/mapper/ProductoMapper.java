package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.Producto;

/**
 * idColorCierre/nombreColorCierre/hexColorCierre no se mapean acá (quedan @Mapping(ignore))
 * porque ya no son un campo directo de Producto — ProductoService.construirRespuesta() los
 * completa aparte leyendo el insumo secundario de tipo "cierre" (ver ProductoInsumoSecundario),
 * manteniendo el mismo contrato de ProductoResponse que existía antes de ese cambio.
 */
@Mapper(componentModel = "spring", uses = { PatronCorteMapper.class, ProductoColorMapper.class, ProductoInsumoSecundarioMapper.class })
public interface ProductoMapper {

    @Mapping(source = "tipoPrenda.id", target = "idTipoPrenda")
    @Mapping(source = "tipoPrenda.nombre", target = "tipoPrenda")
    @Mapping(source = "patronCorte.id", target = "idPatronCorte")
    @Mapping(source = "patronCorte.colores", target = "patronCorteColores")
    @Mapping(source = "tipoTela.codigo", target = "tipoTela")
    @Mapping(target = "idColorCierre", ignore = true)
    @Mapping(target = "nombreColorCierre", ignore = true)
    @Mapping(target = "hexColorCierre", ignore = true)
    @Mapping(target = "subtotal", expression = "java(producto.getCantidadTotal() * producto.getCosto())")
    ProductoResponse toResponse(Producto producto);
}
