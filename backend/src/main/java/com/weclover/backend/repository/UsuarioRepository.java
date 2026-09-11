package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByRolNombreNotAndHabilitadoTrueOrderByNombreAsc(String rolNombre);

    /** Para resolver el vendedor por nombre al importar pedidos desde Excel (ver
     *  PedidoImportService) — la columna "Responsable" de Kommo trae un nombre corto/apodo
     *  (ej. "Sofi"), no un id ni un email. */
    Optional<Usuario> findFirstByNombreContainingIgnoreCaseAndHabilitadoTrue(String fragmentoNombre);
}
