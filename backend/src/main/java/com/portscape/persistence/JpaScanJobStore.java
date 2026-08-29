package com.portscape.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanJobStore;

/**
 * Persistencia dos scans em Postgres.
 *
 * <p>Na fase 1 isto era um {@code ConcurrentHashMap} e o historico morria a cada
 * restart. Sem historico nao ha comparacao com baseline, que e a base do
 * "dispositivo novo destaca-se" do produto -- daí a mudanca.
 */
@Component
public class JpaScanJobStore implements ScanJobStore {

    private final ScanRepository repository;

    public JpaScanJobStore(ScanRepository repository) {
        this.repository = repository;
    }

    /**
     * Grava o job, criando-o se for a primeira vez.
     *
     * <p>Le a entidade existente e altera-a, em vez de fazer {@code merge} de uma
     * entidade destacada: com colecoes sob {@code orphanRemoval}, mexer na entidade
     * gerida e o caminho previsivel -- o merge de uma colecao nova e onde estas
     * coisas costumam falhar em silencio.
     */
    @Override
    @Transactional
    public void save(ScanJob job) {
        ScanEntity entity = repository.findById(job.id())
                .orElseGet(() -> new ScanEntity(job.id()));
        ScanEntityMapper.apply(job, entity);
        repository.save(entity);
    }

    @Override
    @Transactional
    public void updateProgress(UUID id, int progress) {
        repository.updateProgress(id, progress);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScanJob> find(UUID id) {
        return repository.findById(id).map(ScanEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScanJob> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(ScanEntityMapper::toDomain)
                .toList();
    }
}
