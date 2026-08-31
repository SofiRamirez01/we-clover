package com.weclover.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.ArticuloStock;
import com.weclover.backend.entity.PaletaColores;

public interface ArticuloStockRepository extends JpaRepository<ArticuloStock, Long> {

    Optional<ArticuloStock> findByPaletaColor(PaletaColores paletaColor);

    boolean existsByPaletaColor(PaletaColores paletaColor);
}
