package com.weclover.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.SesionPriorizacion;

@Repository
public interface SesionPriorizacionRepository extends JpaRepository<SesionPriorizacion, Long> {
}
