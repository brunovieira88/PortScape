package com.portscape.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portscape.domain.ScanStatus;

public interface ScanRepository extends JpaRepository<ScanEntity, UUID> {

    /** Mais recentes primeiro -- e a ordem que a lista de scans no frontend quer. */
    List<ScanEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Baseline implicito: o ultimo scan concluido desta rede.
     *
     * <p>Quando isto e chamado durante um scan, o scan em curso ainda esta RUNNING e
     * por isso nunca se compara consigo proprio.
     */
    Optional<ScanEntity> findFirstByTargetAndStatusAndIdNotOrderByFinishedAtDesc(
            String target, ScanStatus status, UUID excludedId);
}
