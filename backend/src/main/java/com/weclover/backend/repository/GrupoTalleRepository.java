package com.weclover.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.GrupoTalle;

public interface GrupoTalleRepository extends JpaRepository<GrupoTalle, Long> {

    Optional<GrupoTalle> findByNombre(String nombre);
}
