package com.weclover.backend.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.weclover.backend.config.KommoProperties;
import com.weclover.backend.dto.kommo.KommoCampoPersonalizado;
import com.weclover.backend.dto.kommo.VendedorKommoResponse;

import lombok.RequiredArgsConstructor;

/**
 * Opciones del campo "Vendedor" de los leads de Kommo, consultadas en vivo, para vincular cada
 * una a un usuario del sistema desde la pantalla de usuarios (ver Usuario.kommoVendedorEnumId).
 */
@Service
@RequiredArgsConstructor
public class KommoVendedorService {

    private final KommoApiClient kommoApiClient;
    private final KommoProperties kommoProperties;
    private final AutorizacionService autorizacionService;

    /** Mismo permiso que la pantalla de usuarios, que es la que lo usa. */
    public List<VendedorKommoResponse> listarVendedores(Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        kommoApiClient.verificarConfiguracion();

        KommoCampoPersonalizado campo = kommoApiClient.obtenerCampoDeLeads(kommoProperties.campos().vendedor());
        if (campo.enums() == null) return List.of();
        return campo.enums().stream()
            .sorted(Comparator.comparing(
                (KommoCampoPersonalizado.Opcion opcion) -> opcion.sort() == null ? Integer.MAX_VALUE : opcion.sort()))
            .map(opcion -> new VendedorKommoResponse(opcion.id(), opcion.value()))
            .toList();
    }
}
