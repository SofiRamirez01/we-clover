package com.weclover.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.TipoPrenda;

@Repository
public interface TipoPrendaRepository extends JpaRepository<TipoPrenda, Long> {

    Optional<TipoPrenda> findByNombre(String nombre);
}
