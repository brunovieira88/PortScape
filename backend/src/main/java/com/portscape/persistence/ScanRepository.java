package com.portscape.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.portscape.domain.ScanStatus;

public interface ScanRepository extends JpaRepository<ScanEntity, UUID> {

    /**
     * Escreve o progresso sem passar pela entidade inteira.
     *
     * <p>As duas condicoes extra fecham a corrida entre o thread que le o pipe do nmap
     * e o thread do scan: o primeiro publica progresso, o segundo grava o estado final.
     * Sem o {@code status = RUNNING}, uma atualizacao atrasada podia aterrar depois do
     * DONE e por um scan concluido a mostrar 87%; sem o {@code progress < :progress},
     * duas atualizacoes fora de ordem faziam a barra recuar.
     */
    @Modifying
    @Query("UPDATE ScanEntity s SET s.progress = :progress WHERE s.id = :id"
            + " AND s.status = com.portscape.domain.ScanStatus.RUNNING"
            + " AND s.progress < :progress")
    void updateProgress(UUID id, int progress);

    /** Mais recentes primeiro -- e a ordem que a lista de scans no frontend quer. */
    List<ScanEntity> findAllByOrderByCreatedAtDesc();

    List<ScanEntity> findAllByStatusIn(Collection<ScanStatus> statuses);

    /**
     * Baseline implicito: o ultimo scan concluido desta rede.
     *
     * <p>Quando isto e chamado durante um scan, o scan em curso ainda esta RUNNING e
     * por isso nunca se compara consigo proprio.
     */
    Optional<ScanEntity> findFirstByTargetAndStatusAndIdNotOrderByFinishedAtDesc(
            String target, ScanStatus status, UUID excludedId);
}
