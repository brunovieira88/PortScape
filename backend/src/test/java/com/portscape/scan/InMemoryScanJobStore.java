package com.portscape.scan;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Store em memoria para os testes de orquestracao do {@link ScanService}.
 *
 * <p>A logica que interessa testar la (duas fases, degradacao graciosa, transicoes de
 * estado) nao tem nada a ver com persistencia, e nao vale a pena arrancar um Postgres
 * para a exercitar. A implementacao real e coberta pelo {@code JpaScanJobStoreIT}.
 */
public class InMemoryScanJobStore implements ScanJobStore {

    private final ConcurrentMap<UUID, ScanJob> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(ScanJob job) {
        jobs.put(job.id(), job);
    }

    @Override
    public Optional<ScanJob> find(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<ScanJob> findAll() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(ScanJob::createdAt).reversed())
                .toList();
    }
}
