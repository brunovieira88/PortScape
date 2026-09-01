package com.portscape.scan;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.portscape.baseline.BaselineResolver;
import com.portscape.baseline.BaselineSnapshot;
import com.portscape.config.AsyncConfig;
import com.portscape.config.NmapProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.risk.RiskScore;
import com.portscape.risk.RiskScorer;
import com.portscape.risk.nvd.CveLookupResult;
import com.portscape.risk.nvd.CveLookupService;
import com.portscape.scan.exception.ScanException;
import com.portscape.scan.exception.ScanQueueFullException;

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

    /**
     * Tecto do progresso reportado pela fase de descoberta. Os pontos que sobram sao a
     * deteccao de versao, a consulta de CVEs e o scoring -- trabalho real que acontece
     * depois de o nmap dizer 100%. Uma barra que chega ao fim e fica la parada e pior
     * do que uma barra que chega a 90 e acaba de vez.
     */
    private static final int DISCOVERY_PROGRESS_CEILING = 90;

    /**
     * Tecto do progresso durante a deteccao de versao. Os ultimos pontos ficam para a
     * consulta de CVEs e o scoring; so o {@code done()} e que chega a 100.
     */
    private static final int VERSION_PROGRESS_CEILING = 99;

    private static final String QUEUE_FULL_CODE = new ScanQueueFullException("").code();

    private final TargetValidator targetValidator;
    private final NmapCommandBuilder commandBuilder;
    private final NmapExecutor executor;
    private final NmapXmlParser parser;
    private final ScanJobStore store;
    private final NmapProperties properties;
    private final LocalNetworkDetector localNetworkDetector;
    private final CveLookupService cveLookupService;
    private final RiskScorer riskScorer;
    private final BaselineResolver baselineResolver;
    private final Executor scanExecutor;
    private final Clock clock;

    public ScanService(TargetValidator targetValidator,
                       NmapCommandBuilder commandBuilder,
                       NmapExecutor executor,
                       NmapXmlParser parser,
                       ScanJobStore store,
                       NmapProperties properties,
                       LocalNetworkDetector localNetworkDetector,
                       CveLookupService cveLookupService,
                       RiskScorer riskScorer,
                       BaselineResolver baselineResolver,
                       @Qualifier(AsyncConfig.SCAN_EXECUTOR) Executor scanExecutor,
                       Clock clock) {
        this.targetValidator = targetValidator;
        this.commandBuilder = commandBuilder;
        this.executor = executor;
        this.parser = parser;
        this.store = store;
        this.properties = properties;
        this.localNetworkDetector = localNetworkDetector;
        this.cveLookupService = cveLookupService;
        this.riskScorer = riskScorer;
        this.baselineResolver = baselineResolver;
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
        try {
            scanExecutor.execute(() -> runScan(job.id()));
        } catch (RejectedExecutionException e) {
            // A fila encheu. Deixar o job em PENDING era o pior dos mundos: ninguem o ia
            // correr e o frontend ficava a fazer polling de um scan que nunca comeca.
            store.save(job.failed(QUEUE_FULL_CODE,
                    "A fila de scans esta cheia; este pedido nao chegou a ser agendado.",
                    clock.instant()));
            throw new ScanQueueFullException(
                    "Ja ha scans que cheguem em fila. Espera que os atuais terminem e tenta outra vez.");
        }

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

    public void deleteScan(UUID id) {
        store.delete(id);
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
        Optional<ScanJob> pending = store.find(id);
        if (pending.isEmpty()) {
            // Apagado entre o agendamento e a sua vez na fila. Nada a fazer -- e deixar
            // rebentar aqui era pior: a excecao morria no pool, sem log nem estado.
            log.warn("Scan {} ja nao existe; nada para executar", id);
            return;
        }

        ScanJob job = pending.get().running(clock.instant());
        store.save(job);

        AtomicInteger reported = new AtomicInteger();
        try {
            List<Host> discovered = parser.parse(executor.execute(
                    commandBuilder.buildDiscovery(job.target()),
                    taskPercent -> publishProgress(id, reported,
                            Math.clamp(taskPercent, 0, 100) * DISCOVERY_PROGRESS_CEILING / 100)));
            ScoredHosts scored = withRiskScores(job.target(),
                    withServiceVersions(id, reported, discovered));
            store.save(job.done(scored.hosts(), clock.instant(), scored.cveLookupDegraded()));
            log.info("Scan {} concluido: {} host(s)", id, scored.hosts().size());
        } catch (ScanException e) {
            log.warn("Scan {} falhou [{}]: {}", id, e.code(), e.getMessage());
            store.save(job.failed(e.code(), e.getMessage(), clock.instant()));
        } catch (RuntimeException e) {
            log.error("Scan {} falhou de forma inesperada", id, e);
            store.save(job.failed("UNEXPECTED_ERROR", e.toString(), clock.instant()));
        }
    }

    /**
     * Traduz a percentagem de uma tarefa do nmap num progresso do scan.
     *
     * <p>E uma aproximacao assumida. O nmap reporta progresso <b>por tarefa</b> ("SYN
     * Stealth Scan", "OS detection", ...) e nao diz quantas tarefas ainda faltam, por
     * isso nao ha forma honesta de calcular uma percentagem global. O que se garante
     * sao as duas propriedades que uma barra precisa mesmo de ter: <b>nunca anda para
     * tras</b> -- sem isto o valor caia a zero a cada tarefa nova -- e <b>nunca chega
     * ao fim antes do scan</b>.
     */
    private void publishProgress(UUID id, AtomicInteger reported, int globalPercent) {
        int bounded = Math.clamp(globalPercent, 0, VERSION_PROGRESS_CEILING);
        store.updateProgress(id, reported.accumulateAndGet(bounded, Math::max));
    }

    /**
     * Calcula o risco de cada host.
     *
     * <p>Corre aqui, antes de gravar, e o resultado fica persistido: o score depende
     * dos CVEs que o NVD conhecia neste momento e do baseline que existia agora.
     * Recalcular semanas depois daria outro numero e o historico deixava de ser
     * comparavel consigo proprio.
     */
    private ScoredHosts withRiskScores(String target, List<Host> hosts) {
        if (hosts.isEmpty()) {
            return new ScoredHosts(hosts, false);
        }
        CveLookupResult cves = cveLookupService.lookup(hosts);
        List<Host> baseline = baselineResolver.resolveFor(target)
                .map(BaselineSnapshot::hosts)
                .orElse(null);

        Map<String, RiskScore> scores = riskScorer.score(hosts, cves, baseline);
        return new ScoredHosts(hosts.stream()
                .map(host -> host.withRisk(scores.getOrDefault(host.ip(), RiskScore.none())))
                .toList(), cves.degraded());
    }

    /** Hosts pontuados, com a nota de se a consulta de CVEs ficou incompleta. */
    private record ScoredHosts(List<Host> hosts, boolean cveLookupDegraded) {
    }

    /**
     * Segunda fase, agrupada por conjunto de portas.
     *
     * <p>Uma unica invocacao com a uniao de todas as portas de todos os hosts seria mais
     * simples, mas obrigava o nmap a sondar cada porta em cada maquina: numa rede com 20
     * hosts e 30 portas distintas sao 600 sondas para obter as ~60 que interessam, e as
     * portas filtradas pagam-se em timeout. Agrupar hosts com exatamente as mesmas
     * portas abertas -- coisa comum numa rede real, onde metade dos aparelhos so tem 80
     * e 443 -- sonda apenas o que existe, sem cair no extremo oposto de um processo do
     * nmap por maquina.
     *
     * <p>Um grupo que falhe nao leva os outros atras: os hosts desse grupo ficam com o
     * que a descoberta encontrou, os restantes ficam com a versao.
     */
    private List<Host> withServiceVersions(UUID scanId, AtomicInteger reported, List<Host> discovered) {
        Map<List<Integer>, List<String>> hostsByPortSet = new LinkedHashMap<>();
        for (Host host : discovered) {
            List<Integer> ports = host.ports().stream()
                    .map(Port::number).distinct().sorted().toList();
            if (ports.isEmpty()) {
                continue;
            }
            hostsByPortSet.computeIfAbsent(ports, key -> new ArrayList<>()).add(host.ip());
        }
        if (hostsByPortSet.isEmpty()) {
            // Nada para inquirir sobre versao -- sem hosts up ou sem portas abertas.
            return discovered;
        }

        List<Host> versionInfo = new ArrayList<>();
        int groupsDone = 0;
        for (Map.Entry<List<Integer>, List<String>> group : hostsByPortSet.entrySet()) {
            try {
                versionInfo.addAll(parser.parse(executor.execute(
                        commandBuilder.buildVersionDetection(group.getValue(), group.getKey()))));
            } catch (ScanException e) {
                log.warn("Scan {}: deteccao de versao falhou para {} [{}], esses hosts ficam so com portas+OS: {}",
                        scanId, group.getValue(), e.code(), e.getMessage());
            }
            // Um grupo terminado e a unica unidade de progresso honesta desta fase: o
            // nmap nao reporta nada util aqui e sem isto a barra parava nos 90 ate ao fim.
            groupsDone++;
            publishProgress(scanId, reported, DISCOVERY_PROGRESS_CEILING
                    + (VERSION_PROGRESS_CEILING - DISCOVERY_PROGRESS_CEILING)
                    * groupsDone / hostsByPortSet.size());
        }
        return ScanResultMerger.merge(discovered, versionInfo);
    }
}
