package com.portscape.baseline;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portscape.domain.ScanStatus;
import com.portscape.persistence.BaselineEntity;
import com.portscape.persistence.BaselineRepository;
import com.portscape.persistence.ScanEntity;
import com.portscape.persistence.ScanRepository;
import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanJobStore;

/**
 * Fixa e liberta baselines, e calcula o diff de um scan.
 */
@Service
public class BaselineService {

    private static final Logger log = LoggerFactory.getLogger(BaselineService.class);

    private final BaselineRepository baselineRepository;
    private final ScanRepository scanRepository;
    private final ScanJobStore store;
    private final BaselineResolver resolver;
    private final Clock clock;

    public BaselineService(BaselineRepository baselineRepository,
                           ScanRepository scanRepository,
                           ScanJobStore store,
                           BaselineResolver resolver,
                           Clock clock) {
        this.baselineRepository = baselineRepository;
        this.scanRepository = scanRepository;
        this.store = store;
        this.resolver = resolver;
        this.clock = clock;
    }

    /**
     * Fixa um scan como referencia da rede que ele cobre.
     *
     * <p>So um scan concluido serve: fixar um scan que falhou daria um baseline vazio,
     * contra o qual toda a rede apareceria como nova no scan seguinte.
     *
     * @throws BaselineNotAllowedException se o scan nao existir ou nao estiver DONE
     */
    @Transactional
    public Baseline pin(UUID scanId) {
        ScanEntity scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new BaselineNotAllowedException("Scan nao encontrado: " + scanId));
        if (scan.getStatus() != ScanStatus.DONE) {
            throw new BaselineNotAllowedException(
                    "So um scan concluido pode servir de baseline; este esta em " + scan.getStatus());
        }

        BaselineEntity entity = baselineRepository.findById(scan.getTarget())
                .orElseGet(() -> new BaselineEntity(scan.getTarget(), scan, clock.instant()));
        entity.setScan(scan);
        entity.setPinnedAt(clock.instant());
        baselineRepository.save(entity);

        log.info("Baseline de {} fixado no scan {}", scan.getTarget(), scanId);
        return toDomain(entity);
    }

    /** @return true se havia mesmo um baseline fixado para remover */
    @Transactional
    public boolean unpin(String target) {
        if (!baselineRepository.existsById(target)) {
            return false;
        }
        baselineRepository.deleteById(target);
        log.info("Baseline fixado de {} removido; volta a comparar com o scan anterior", target);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Baseline> findAll() {
        return baselineRepository.findAllByOrderByTargetAsc().stream()
                .map(BaselineService::toDomain)
                .toList();
    }

    /**
     * Compara um scan com o baseline <b>atual</b> da sua rede.
     *
     * <p>Calculado agora e nao gravado: se alguem fixar outro baseline, esta resposta
     * reflete essa escolha imediatamente, sem reescrever historico.
     */
    @Transactional(readOnly = true)
    public Optional<ScanDiff> diffFor(UUID scanId) {
        return store.find(scanId).map(scan -> {
            if (scan.status() != ScanStatus.DONE) {
                return ScanDiff.none();
            }
            return ScanDiffer.diff(scan, resolver.resolveFor(scan.target(), scanId).orElse(null));
        });
    }

    /** O diff de um scan ja carregado, sem o voltar a ler da base de dados. */
    @Transactional(readOnly = true)
    public ScanDiff diffFor(ScanJob scan) {
        if (scan.status() != ScanStatus.DONE) {
            return ScanDiff.none();
        }
        return ScanDiffer.diff(scan, resolver.resolveFor(scan.target(), scan.id()).orElse(null));
    }

    private static Baseline toDomain(BaselineEntity entity) {
        return new Baseline(entity.getTarget(), entity.getScanId(), entity.getPinnedAt());
    }
}
