package com.portscape.scan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Onde os scans ficam guardados.
 *
 * <p>Interface e nao classe por uma razao concreta: o {@link ScanService} orquestra
 * scans e a sua logica (duas fases, degradacao graciosa, estados de falha) merece
 * testes rapidos que nao arrastem um Postgres atras. A implementacao real e
 * {@link com.portscape.persistence.JpaScanJobStore}; os testes usam uma em memoria.
 */
public interface ScanJobStore {

    void save(ScanJob job);

    Optional<ScanJob> find(UUID id);

    /** Mais recentes primeiro -- e a ordem que a lista de scans no frontend quer. */
    List<ScanJob> findAll();
}
