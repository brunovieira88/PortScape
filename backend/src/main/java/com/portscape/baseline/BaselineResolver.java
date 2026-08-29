package com.portscape.baseline;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portscape.domain.ScanStatus;
import com.portscape.persistence.BaselineRepository;
import com.portscape.persistence.ScanEntityMapper;
import com.portscape.persistence.ScanRepository;
import com.portscape.scan.ScanJob;

/**
 * Decide contra o que um scan e comparado, por esta ordem:
 *
 * <ol>
 *   <li>o scan <b>fixado</b> como referencia para aquela rede, se existir -- serve para
 *       congelar um estado que se sabe limpo e medir tudo contra ele;</li>
 *   <li>senao, o ultimo scan concluido da mesma rede -- deteta o que mudou desde a
 *       ultima vez sem exigir nenhuma configuracao;</li>
 *   <li>senao, nenhum. No primeiro scan de uma rede nao ha termo de comparacao, e
 *       marcar todos os hosts como novos seria ruido em vez de sinal.</li>
 * </ol>
 */
@Service
public class BaselineResolver {

    private static final Logger log = LoggerFactory.getLogger(BaselineResolver.class);

    private final ScanRepository scanRepository;
    private final BaselineRepository baselineRepository;

    public BaselineResolver(ScanRepository scanRepository, BaselineRepository baselineRepository) {
        this.scanRepository = scanRepository;
        this.baselineRepository = baselineRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ScanJob> resolveFor(String target) {
        return resolveFor(target, null);
    }

    /**
     * @param excludeScanId scan a excluir da procura -- ao comparar um scan ja gravado
     *                      com o anterior, ele proprio nao pode ser o seu baseline
     */
    @Transactional(readOnly = true)
    public Optional<ScanJob> resolveFor(String target, UUID excludeScanId) {
        Optional<ScanJob> pinned = baselineRepository.findById(target)
                .map(baseline -> baseline.getScan().getId())
                .filter(id -> !id.equals(excludeScanId))
                .flatMap(scanRepository::findById)
                .map(ScanEntityMapper::toDomain);
        if (pinned.isPresent()) {
            log.debug("Baseline fixado para {}: scan {}", target, pinned.get().id());
            return pinned;
        }
        return previousScan(target, excludeScanId);
    }

    private Optional<ScanJob> previousScan(String target, UUID excludeScanId) {
        return scanRepository
                .findFirstByTargetAndStatusAndIdNotOrderByFinishedAtAsc(
                        target, ScanStatus.DONE, excludeScanId == null ? ZERO_UUID : excludeScanId)
                .map(ScanEntityMapper::toDomain);
    }

    /** Nenhum scan tem este id, por isso o "id <> ?" nao exclui nada quando nao ha exclusao. */
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
}
