package com.weclover.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.SincronizacionKommo;

@Repository
public interface SincronizacionKommoRepository extends JpaRepository<SincronizacionKommo, Long> {

    Optional<SincronizacionKommo> findFirstByOrderByFechaHoraDescIdDesc();
}
