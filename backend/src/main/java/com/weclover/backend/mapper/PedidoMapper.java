package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.entity.Pedido;

@Mapper(componentModel = "spring", uses = ProductoMapper.class)
public interface PedidoMapper {

    @Mapping(source = "colegio.id", target = "idColegio")
    @Mapping(source = "colegio.nombre", target = "nombreColegio")
    @Mapping(source = "colegio.localidad", target = "localidadColegio")
    @Mapping(source = "representanteCurso.id", target = "idRepresentanteCurso")
    @Mapping(source = "representanteCurso.nombre", target = "nombreRepresentanteCurso")
    @Mapping(source = "creadoPor.id", target = "idVendedor")
    @Mapping(source = "creadoPor.nombre", target = "nombreVendedor")
    @Mapping(target = "precioTotal", ignore = true)
    @Mapping(target = "saldo", ignore = true)
    @Mapping(target = "porcentajePagado", ignore = true)
    PedidoResponse toResponse(Pedido pedido);
}
