package com.portscape.scan;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Guarda os scans em memoria.
 *
 * <p>Deliberadamente volatil nesta fase: a fase 2 substitui isto por Postgres, que
 * e o que permite historico e comparacao com baseline. Ate la, reiniciar a
 * aplicacao apaga os scans -- e aceitavel e evita arrastar JPA para a fase 1.
 */
@Component
public class ScanJobStore {

    private final ConcurrentMap<UUID, ScanJob> jobs = new ConcurrentHashMap<>();

    public void save(ScanJob job) {
        jobs.put(job.id(), job);
    }

    public Optional<ScanJob> find(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /** Mais recentes primeiro -- e a ordem que a lista de scans no frontend quer. */
    public List<ScanJob> findAll() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(ScanJob::createdAt).reversed())
                .toList();
    }
}
