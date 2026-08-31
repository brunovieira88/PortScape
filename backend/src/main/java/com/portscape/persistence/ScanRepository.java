package com.portscape.persistence;

import java.time.Instant;

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
     * Baseline implicito de um scan <b>em curso</b>: o ultimo concluido desta rede.
     *
     * <p>O scan em curso ainda esta RUNNING, por isso nunca se compara consigo proprio.
     */
    Optional<ScanEntity> findFirstByTargetAndStatusAndIdNotOrderByFinishedAtDesc(
            String target, ScanStatus status, UUID excludedId);

    /**
     * Baseline implicito de um scan <b>ja gravado</b>: o ultimo concluido desta rede
     * que terminou antes dele.
     *
     * <p>O filtro pelo instante nao e um detalhe. Sem ele, abrir um scan antigo no
     * historico devolvia como baseline o scan mais recente da rede -- um scan
     * posterior a ele -- e o diff saia invertido: um host que existia na altura e
     * desapareceu depois aparecia marcado como novo. O ultimo scan nunca dava por isso,
     * porque para ele "o mais recente tirando eu" ja e o anterior.
     *
     * <p>O {@code idNot} e redundante face ao {@code finishedAtBefore} -- um scan nao
     * termina antes de si proprio -- mas cobre o empate de dois scans com o mesmo
     * instante.
     */
    Optional<ScanEntity> findFirstByTargetAndStatusAndIdNotAndFinishedAtBeforeOrderByFinishedAtDesc(
            String target, ScanStatus status, UUID excludedId, Instant before);
}
