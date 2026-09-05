package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.portscape.baseline.BaselineResolver;
import com.portscape.config.NmapProperties;
import com.portscape.domain.ScanStatus;
import org.springframework.web.client.RestClient;

import com.portscape.config.KevProperties;
import com.portscape.config.NvdProperties;
import com.portscape.risk.RiskScorer;
import com.portscape.risk.kev.KevCatalog;
import com.portscape.risk.nvd.PortCveEnricher;
import com.portscape.risk.nvd.CveLookupService;

/**
 * Cancelar um scan mata mesmo o processo do scanner.
 *
 * <p>E a garantia central da funcionalidade e a unica que nenhum outro teste toca: os
 * testes do {@link ScanService} substituem o {@link NmapExecutor} por um duplo, e
 * portanto provam que o <i>estado</i> fica CANCELLED, nao que o processo morre. Um
 * cancelamento que deixe um nmap a correr na maquina e pior do que nao ter botao
 * nenhum -- o utilizador julga que parou, e a rede continua a ser sondada.
 *
 * <p>Aqui o executor e o pool sao os reais e o processo tambem: em vez do nmap corre
 * um comando demorado de sistema. O que se verifica e a mecanica de
 * {@code destroyForcibly} no caminho da interrupcao, que e exactamente o que o
 * cancelamento usa -- so o binario e que e outro.
 *
 * <p>O processo e encontrado pelos descendentes desta JVM e nao pela linha de comando:
 * no Windows o {@code ProcessHandle.info().commandLine()} vem vazio, e um teste que
 * procurasse por ai passava sempre, por nunca encontrar nada.
 */
class ScanCancellationTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase().startsWith("windows");

    /** Um processo que dura o suficiente para dar tempo a cancela-lo. */
    private static List<String> longRunningCommand() {
        return WINDOWS
                ? List.of("ping", "-n", "60", "127.0.0.1")
                : List.of("sleep", "60");
    }

    /** Os processos que esta JVM lancou -- na pratica, o do scanner. */
    private static List<ProcessHandle> children() {
        return ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).toList();
    }

    @Test
    @DisplayName("cancelar um scan mata o processo do scanner, e nao so o estado do job")
    void cancellingKillsTheProcess() throws Exception {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.initialize();

        InMemoryScanJobStore store = new InMemoryScanJobStore();
        ScanService service = serviceRunning(longRunningCommand(), store, pool);

        try {
            ScanJob started = service.startScan("192.168.1.0/24");
            UUID id = started.id();

            // Espera que o processo exista mesmo antes de o mandar abaixo -- sem isto o
            // teste passava por o processo ainda nao ter arrancado.
            waitUntil("o processo do scanner arrancar", () -> !children().isEmpty());
            List<ProcessHandle> spawned = children();
            assertThat(spawned).isNotEmpty();
            assertThat(store.find(id).orElseThrow().status()).isEqualTo(ScanStatus.RUNNING);

            service.cancelScan(id);

            // O destroyForcibly nao e instantaneo: o SO ainda tem de reclamar o processo.
            waitUntil("o processo do scanner morrer",
                    () -> spawned.stream().noneMatch(ProcessHandle::isAlive));
            assertThat(spawned).noneMatch(ProcessHandle::isAlive);
            assertThat(store.find(id).orElseThrow().status()).isEqualTo(ScanStatus.CANCELLED);
        } finally {
            pool.shutdown();
            // Se o teste falhar a meio, nao fica um processo pendurado na maquina.
            children().forEach(ProcessHandle::destroyForcibly);
        }
    }

    /** O servico com o executor e o pool reais; so o alvo do comando e que e falso. */
    private static ScanService serviceRunning(List<String> command,
                                              ScanJobStore store,
                                              AsyncTaskExecutor pool) {
        NmapProperties properties = new NmapProperties(
                List.of("/usr/bin/nmap"), "192.168.1.0/24", List.of(), null,
                Duration.ofMinutes(5), null);

        NmapCommandBuilder commandBuilder = mock(NmapCommandBuilder.class);
        when(commandBuilder.buildDiscovery(anyString())).thenReturn(command);

        CveLookupService cveLookup = mock(CveLookupService.class);
        BaselineResolver baselineResolver = mock(BaselineResolver.class);
        when(baselineResolver.resolveFor(anyString())).thenReturn(Optional.empty());

        NmapXmlParser parser = mock(NmapXmlParser.class);
        when(parser.parse(anyString())).thenReturn(List.of());

        LocalNetworkDetector detector = mock(LocalNetworkDetector.class);
        when(detector.detectLocalSubnet()).thenReturn(Optional.empty());

        return new ScanService(
                new TargetValidator(properties),
                commandBuilder,
                new NmapExecutor(properties),
                parser,
                store,
                properties,
                detector,
                cveLookup,
                disabledKev(),
                defaultEnricher(),
                new RiskScorer(List.of()),
                baselineResolver,
                pool,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    /** Espera por uma condicao com prazo, para o teste falhar em vez de ficar preso. */
    private static void waitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Passaram 15s sem " + what);
    }

    /**
     * O catalogo desligado: nao sai para a rede e devolve os CVEs como vieram. O que
     * o KEV faz e testado no KevCatalogTest -- aqui so nao pode estorvar.
     */
    private static KevCatalog disabledKev() {
        return new KevCatalog(RestClient.create(),
                new KevProperties(false, null, null, null), Clock.systemUTC());
    }

    /** O enricher com os defaults da configuracao -- o tecto de CVEs e testado a parte. */
    private static PortCveEnricher defaultEnricher() {
        return new PortCveEnricher(
                new NvdProperties(true, null, null, null, null, null, null, null));
    }
}
