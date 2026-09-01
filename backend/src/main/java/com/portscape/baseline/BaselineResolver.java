package com.portscape.baseline;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portscape.config.BaselineProperties;
import com.portscape.domain.Host;
import com.portscape.persistence.ScanEntity;
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
 *   <li>senao, o <b>inventario</b> da rede: os dispositivos vistos na janela de
 *       {@code portscape.baseline.window}, cada um no seu estado mais recente;</li>
 *   <li>senao, nenhum. No primeiro scan de uma rede nao ha termo de comparacao, e
 *       marcar todos os hosts como novos seria ruido em vez de sinal.</li>
 * </ol>
 *
 * <p>O inventario substituiu "o scan anterior", que respondia mal a pergunta de uma
 * auditoria: um dispositivo desligado ha tres scans deixava de aparecer como offline
 * porque tambem ja nao estava no scan anterior, e um que ia e voltava era assinalado
 * como novo de cada vez que reaparecia.
 *
 * <p>Isto so e seguro desde que a identidade passou a ser o MAC (ver
 * {@link com.portscape.domain.Host#identity()}). Acumular historico com o IP por
 * identidade enchia o inventario de fantasmas a cada renovacao de DHCP.
 */
@Service
public class BaselineResolver {

    private static final Logger log = LoggerFactory.getLogger(BaselineResolver.class);

    private final ScanRepository scanRepository;
    private final BaselineRepository baselineRepository;
    private final BaselineProperties properties;
    private final Clock clock;

    public BaselineResolver(ScanRepository scanRepository, BaselineRepository baselineRepository,
            BaselineProperties properties, Clock clock) {
        this.scanRepository = scanRepository;
        this.baselineRepository = baselineRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Baseline de um scan <b>em curso</b>: o inventario ate agora.
     */
    @Transactional(readOnly = true)
    public Optional<BaselineSnapshot> resolveFor(String target) {
        return pinnedFor(target, null)
                .or(() -> inventoryOf(target, clock.instant(), null));
    }

    /**
     * Baseline de um scan <b>ja gravado</b>: o inventario tal como estava imediatamente
     * antes dele, para que abrir um scan do historico mostre o que se sabia na altura e
     * nao o que se sabe hoje.
     *
     * <p>O baseline fixado nao leva filtro de data, de proposito: fixar um scan e dizer
     * "medir tudo contra isto", e descarta-lo para os scans anteriores ao que foi
     * fixado esvaziava a escolha. So nao se aplica a si proprio.
     */
    @Transactional(readOnly = true)
    public Optional<BaselineSnapshot> resolveFor(ScanJob scan) {
        Optional<BaselineSnapshot> pinned = pinnedFor(scan.target(), scan.id());
        if (pinned.isPresent()) {
            return pinned;
        }
        Instant before = scan.finishedAt() == null ? clock.instant() : scan.finishedAt();
        return inventoryOf(scan.target(), before, scan.id());
    }

    /**
     * Une os scans da janela, do mais antigo para o mais recente, ficando com o ultimo
     * estado conhecido de cada dispositivo.
     *
     * <p>A chave e {@link com.portscape.domain.Host#identity()}: o MAC quando o nmap o
     * resolveu, o IP quando nao. Sem isso, um dispositivo que mudasse de IP entrava no
     * inventario duas vezes e a versao velha ficava la como offline para sempre.
     */
    private Optional<BaselineSnapshot> inventoryOf(String target, Instant before, UUID exclude) {
        Instant from = before.minus(properties.window());
        List<ScanEntity> window = scanRepository.findInventoryWindow(target, from, before);

        Map<String, Host> byIdentity = new LinkedHashMap<>();
        UUID mostRecent = null;
        for (ScanEntity entity : window) {
            if (entity.getId().equals(exclude)) {
                continue;
            }
            ScanJob job = ScanEntityMapper.toDomain(entity);
            for (Host host : job.hosts()) {
                byIdentity.put(host.identity(), host);
            }
            mostRecent = job.id();
        }

        if (mostRecent == null) {
            return Optional.empty();
        }
        log.debug("Inventario de {}: {} dispositivos em {} scans desde {}",
                target, byIdentity.size(), window.size(), from);
        return Optional.of(new BaselineSnapshot(mostRecent, List.copyOf(byIdentity.values())));
    }

    private Optional<BaselineSnapshot> pinnedFor(String target, UUID excludeScanId) {
        return baselineRepository.findById(target)
                .map(baseline -> baseline.getScan().getId())
                .filter(id -> !id.equals(excludeScanId))
                .flatMap(scanRepository::findById)
                .map(ScanEntityMapper::toDomain)
                .map(job -> new BaselineSnapshot(job.id(), job.hosts()));
    }

}
