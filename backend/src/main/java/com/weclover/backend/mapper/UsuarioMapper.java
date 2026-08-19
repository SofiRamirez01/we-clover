package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.usuario.UsuarioResponse;
import com.weclover.backend.entity.Usuario;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(source = "rol.id", target = "idRol")
    @Mapping(source = "rol.nombre", target = "rol")
    @Mapping(source = "rol.descripcion", target = "rolDescripcion")
    UsuarioResponse toResponse(Usuario usuario);
}
