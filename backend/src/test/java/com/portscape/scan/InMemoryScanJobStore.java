package com.portscape.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.portscape.domain.ScanStatus;

/**
 * Store em memoria para os testes de orquestracao do {@link ScanService}.
 *
 * <p>A logica que interessa testar la (duas fases, degradacao graciosa, transicoes de
 * estado) nao tem nada a ver com persistencia, e nao vale a pena arrancar um Postgres
 * para a exercitar. A implementacao real e coberta pelo {@code JpaScanJobStoreIT}.
 */
public class InMemoryScanJobStore implements ScanJobStore {

    private final ConcurrentMap<UUID, ScanJob> jobs = new ConcurrentHashMap<>();
    private final List<Integer> progressHistory = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void save(ScanJob job) {
        jobs.put(job.id(), job);
    }

    @Override
    public void updateProgress(UUID id, int progress) {
        progressHistory.add(progress);
        // Mesma garantia que o JpaScanJobStore: so avanca, e so enquanto o scan corre.
        jobs.computeIfPresent(id, (key, existing) ->
                existing.status() == ScanStatus.RUNNING && existing.progress() < progress
                        ? existing.withProgress(progress)
                        : existing);
    }

    /** Todos os valores de progresso publicados, pela ordem em que foram publicados. */
    public List<Integer> progressHistory() {
        return List.copyOf(progressHistory);
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

    @Override
    public List<ScanJob> findUnfinished() {
        return jobs.values().stream()
                .filter(job -> job.status() == ScanStatus.PENDING
                        || job.status() == ScanStatus.RUNNING)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        jobs.remove(id);
    }
}
