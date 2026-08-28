package com.portscape.scan;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.portscape.config.AsyncConfig;
import com.portscape.config.NmapProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.exception.ScanException;

/**
 * Orquestra um scan: valida o target, cria o job e entrega a execucao ao pool.
 *
 * <p>O trabalho e submetido explicitamente ao {@code Executor} em vez de usar
 * {@code @Async}: o metodo assincrono seria chamado de dentro do proprio bean e a
 * anotacao nao se aplica em auto-invocacao -- o scan correria no thread do pedido HTTP.
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final TargetValidator targetValidator;
    private final NmapCommandBuilder commandBuilder;
    private final NmapExecutor executor;
    private final NmapXmlParser parser;
    private final ScanJobStore store;
    private final NmapProperties properties;
    private final LocalNetworkDetector localNetworkDetector;
    private final Executor scanExecutor;
    private final Clock clock;

    public ScanService(TargetValidator targetValidator,
                       NmapCommandBuilder commandBuilder,
                       NmapExecutor executor,
                       NmapXmlParser parser,
                       ScanJobStore store,
                       NmapProperties properties,
                       LocalNetworkDetector localNetworkDetector,
                       @Qualifier(AsyncConfig.SCAN_EXECUTOR) Executor scanExecutor,
                       Clock clock) {
        this.targetValidator = targetValidator;
        this.commandBuilder = commandBuilder;
        this.executor = executor;
        this.parser = parser;
        this.store = store;
        this.properties = properties;
        this.localNetworkDetector = localNetworkDetector;
        this.scanExecutor = scanExecutor;
        this.clock = clock;
    }

    /**
     * Valida o target e agenda o scan. Devolve logo, com o job em PENDING -- um /24
     * demora minutos e nao caberia num pedido HTTP.
     *
     * @param requestedTarget target pedido, ou null/vazio para detetar a subnet
     *                        local automaticamente
     * @throws com.portscape.scan.exception.InvalidTargetException se o target for recusado
     */
    public ScanJob startScan(String requestedTarget) {
        String raw = resolveTarget(requestedTarget);
        String target = targetValidator.validate(raw);

        ScanJob job = ScanJob.pending(UUID.randomUUID(), target, clock.instant());
        store.save(job);
        scanExecutor.execute(() -> runScan(job.id()));

        log.info("Scan {} agendado para {}", job.id(), target);
        return job;
    }

    /**
     * Pedido explicito ganha sempre. Sem pedido, tenta a subnet da rota por
     * defeito do SO; so cai para {@code portscape.nmap.default-target} se a
     * deteccao falhar (sem rota, ambiente isolado).
     */
    private String resolveTarget(String requestedTarget) {
        if (requestedTarget != null && !requestedTarget.isBlank()) {
            return requestedTarget;
        }
        return localNetworkDetector.detectLocalSubnet()
                .orElseGet(() -> {
                    log.warn("Deteccao automatica da rede local falhou; a usar portscape.nmap.default-target: {}",
                            properties.defaultTarget());
                    return properties.defaultTarget();
                });
    }

    public Optional<ScanJob> findScan(UUID id) {
        return store.find(id);
    }

    public List<ScanJob> findAllScans() {
        return store.findAll();
    }

    /**
     * Corre o scan e escreve o resultado no store. Nao deixa escapar excecoes: uma
     * falha aqui e um estado FAILED do job, nao um erro perdido no pool de threads.
     *
     * <p>Duas fases (ver {@link NmapCommandBuilder} para o porque): a descoberta
     * (privilegiada) da portas e OS; a deteccao de versao (sem privilegios) so
     * acrescenta servico/produto/versao. Se a descoberta falhar, o scan falha -- e
     * o resultado principal. Se so a deteccao de versao falhar, o scan continua
     * DONE com o que a descoberta encontrou: portas e OS sao informacao valida por
     * si so, e nao vale a pena deitar fora so por faltar a versao do servico.
     */
    void runScan(UUID id) {
        ScanJob job = store.find(id).orElseThrow();
        job = job.running(clock.instant());
        store.save(job);

        try {
            List<Host> discovered = parser.parse(executor.execute(commandBuilder.buildDiscovery(job.target())));
            List<Host> hosts = withServiceVersions(id, discovered);
            store.save(job.done(hosts, clock.instant()));
            log.info("Scan {} concluido: {} host(s)", id, hosts.size());
        } catch (ScanException e) {
            log.warn("Scan {} falhou [{}]: {}", id, e.code(), e.getMessage());
            store.save(job.failed(e.code(), e.getMessage(), clock.instant()));
        } catch (RuntimeException e) {
            log.error("Scan {} falhou de forma inesperada", id, e);
            store.save(job.failed("UNEXPECTED_ERROR", e.toString(), clock.instant()));
        }
    }

    private List<Host> withServiceVersions(UUID scanId, List<Host> discovered) {
        List<String> hostIps = discovered.stream().map(Host::ip).toList();
        List<Integer> ports = discovered.stream()
                .flatMap(host -> host.ports().stream())
                .map(Port::number)
                .distinct()
                .sorted()
                .toList();

        if (hostIps.isEmpty() || ports.isEmpty()) {
            // Nada para inquirir sobre versao -- sem hosts up ou sem portas abertas.
            return discovered;
        }

        try {
            List<Host> versionInfo = parser.parse(
                    executor.execute(commandBuilder.buildVersionDetection(hostIps, ports)));
            return ScanResultMerger.merge(discovered, versionInfo);
        } catch (ScanException e) {
            log.warn("Scan {}: deteccao de versao falhou [{}], a manter so portas+OS: {}",
                    scanId, e.code(), e.getMessage());
            return discovered;
        }
    }
}
