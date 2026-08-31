package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.stock.StockResponse;
import com.weclover.backend.entity.Stock;
import com.weclover.backend.entity.UnidadMedida;

@Mapper(componentModel = "spring")
public abstract class StockMapper {

    @Mapping(source = "articulo.paletaColor.id", target = "idPaletaColor")
    @Mapping(source = "articulo.paletaColor.nombre", target = "nombreColor")
    @Mapping(source = "articulo.paletaColor.hex", target = "hexColor")
    @Mapping(source = "articulo.paletaColor.tipoTela.id", target = "idTipoTela")
    @Mapping(source = "articulo.paletaColor.tipoTela.codigo", target = "codigoTipoTela")
    @Mapping(source = "articulo.paletaColor.tipoTela.nombre", target = "nombreTipoTela")
    @Mapping(source = "proveedor.id", target = "idProveedor")
    @Mapping(source = "proveedor.nombre", target = "nombreProveedor")
    @Mapping(target = "unidadMedida", expression = "java(resolverUnidadMedida(stock))")
    @Mapping(source = "actualizadoPor.id", target = "idActualizadoPor")
    @Mapping(source = "actualizadoPor.nombre", target = "nombreActualizadoPor")
    public abstract StockResponse toResponse(Stock stock);

    /** KG si la tela se compra por peso, UNIDAD si no — mismo enum que ArticuloProveedor, pero
     *  acá no se persiste: se deriva del tipo de tela en el momento de armar la respuesta. */
    protected UnidadMedida resolverUnidadMedida(Stock stock) {
        boolean esPorPeso = stock.getArticulo().getPaletaColor().getTipoTela().isEsPorPeso();
        return esPorPeso ? UnidadMedida.KG : UnidadMedida.UNIDAD;
    }
}
