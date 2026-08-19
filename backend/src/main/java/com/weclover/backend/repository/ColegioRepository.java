package com.weclover.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.Colegio;

@Repository
public interface ColegioRepository extends JpaRepository<Colegio, Long> {
}
