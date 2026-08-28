package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.portscape.baseline.BaselineResolver;
import com.portscape.config.NmapProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.domain.ScanStatus;
import com.portscape.risk.RiskScorer;
import com.portscape.risk.nvd.CveLookupResult;
import com.portscape.risk.nvd.CveLookupService;
import com.portscape.scan.exception.InvalidTargetException;
import com.portscape.scan.exception.NmapExecutionException;
import com.portscape.scan.exception.NmapPrivilegeException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScanServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

    @Mock
    private NmapExecutor executor;
    @Mock
    private NmapXmlParser parser;
    @Mock
    private LocalNetworkDetector localNetworkDetector;
    @Mock
    private CveLookupService cveLookupService;
    @Mock
    private BaselineResolver baselineResolver;

    private ScanJobStore store;
    private ScanService service;
    /** Executor sincrono: o scan corre dentro do startScan, o que torna o teste determinista. */
    private final Executor directExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        NmapProperties properties = new NmapProperties(
                List.of("/usr/bin/nmap"), "192.168.1.0/24", List.of("-sS"), null, null);
        // Por defeito, como se a maquina do teste nao tivesse rota por defeito:
        // os testes que nao mencionam deteccao caem sempre no default-target.
        when(localNetworkDetector.detectLocalSubnet()).thenReturn(Optional.empty());
        // O scoring e o baseline sao testados a parte; aqui interessa a orquestracao.
        when(cveLookupService.lookup(anyList())).thenReturn(CveLookupResult.empty());
        when(baselineResolver.resolveFor(any())).thenReturn(Optional.empty());
        store = new InMemoryScanJobStore();
        service = new ScanService(
                new TargetValidator(),
                new NmapCommandBuilder(properties),
                executor,
                parser,
                store,
                properties,
                localNetworkDetector,
                cveLookupService,
                new RiskScorer(List.of()),
                baselineResolver,
                directExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completesTheJobWithTheParsedHosts() {
        List<Host> hosts = List.of(new Host("192.168.1.10", "nas.lan", "Linux", 94,
                List.of(new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6"))));
        when(executor.execute(anyList())).thenReturn("<nmaprun/>");
        when(parser.parse(any())).thenReturn(hosts);

        ScanJob started = service.startScan("192.168.1.0/24");
        ScanJob finished = store.find(started.id()).orElseThrow();

        assertThat(finished.status()).isEqualTo(ScanStatus.DONE);
        // O scan enriquece os hosts com o risco, por isso compara-se o que veio do parser.
        assertThat(finished.hosts()).extracting(Host::ip, Host::ports)
                .containsExactly(tuple(hosts.get(0).ip(), hosts.get(0).ports()));
        assertThat(finished.hosts().get(0).risk()).isNotNull();
        assertThat(finished.startedAt()).isEqualTo(NOW);
        assertThat(finished.finishedAt()).isEqualTo(NOW);
        assertThat(finished.errorCode()).isNull();
    }

    @Test
    @DisplayName("o job devolvido pelo POST esta em PENDING, antes do scan comecar")
    void returnsAPendingJobImmediately() {
        when(executor.execute(anyList())).thenReturn("<nmaprun/>");
        when(parser.parse(any())).thenReturn(List.of());

        ScanJob started = service.startScan("192.168.1.0/24");

        assertThat(started.status()).isEqualTo(ScanStatus.PENDING);
        assertThat(started.startedAt()).isNull();
        assertThat(started.id()).isNotNull();
    }

    @Test
    void marksTheJobRunningBeforeExecutingNmap() {
        when(executor.execute(anyList())).thenAnswer(invocation -> {
            // Enquanto o nmap corre, o polling tem de ver RUNNING.
            assertThat(store.findAll()).singleElement()
                    .extracting(ScanJob::status).isEqualTo(ScanStatus.RUNNING);
            return "<nmaprun/>";
        });
        when(parser.parse(any())).thenReturn(List.of());

        service.startScan("192.168.1.0/24");
    }

    @Test
    @DisplayName("uma falha de scan vira estado FAILED, nao uma excecao perdida no pool")
    void recordsScanFailuresOnTheJob() {
        when(executor.execute(anyList())).thenThrow(new NmapPrivilegeException("precisa de root"));

        ScanJob started = service.startScan("192.168.1.0/24");
        ScanJob failed = store.find(started.id()).orElseThrow();

        assertThat(failed.status()).isEqualTo(ScanStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("NMAP_PRIVILEGE");
        assertThat(failed.errorMessage()).contains("precisa de root");
        assertThat(failed.hosts()).isEmpty();
    }

    @Test
    void recordsUnexpectedFailuresToo() {
        when(executor.execute(anyList())).thenThrow(new IllegalStateException("boom"));

        ScanJob started = service.startScan("192.168.1.0/24");

        assertThat(store.find(started.id()).orElseThrow().errorCode()).isEqualTo("UNEXPECTED_ERROR");
    }

    @Test
    @DisplayName("sem target pedido e sem deteccao de rede, cai no default-target configurado")
    void fallsBackToTheConfiguredDefaultTarget() {
        when(executor.execute(anyList())).thenReturn("<nmaprun/>");
        when(parser.parse(any())).thenReturn(List.of());

        assertThat(service.startScan(null).target()).isEqualTo("192.168.1.0/24");
        assertThat(service.startScan("  ").target()).isEqualTo("192.168.1.0/24");
    }

    @Test
    @DisplayName("sem target pedido, usa a subnet detetada em vez do default-target")
    void prefersTheDetectedLocalSubnetOverTheConfiguredDefault() {
        when(localNetworkDetector.detectLocalSubnet()).thenReturn(Optional.of("10.0.5.0/24"));
        when(executor.execute(anyList())).thenReturn("<nmaprun/>");
        when(parser.parse(any())).thenReturn(List.of());

        assertThat(service.startScan(null).target()).isEqualTo("10.0.5.0/24");
    }

    @Test
    @DisplayName("um target pedido explicitamente ganha sempre a deteccao automatica")
    void anExplicitTargetWinsOverDetection() {
        when(localNetworkDetector.detectLocalSubnet()).thenReturn(Optional.of("10.0.5.0/24"));
        when(executor.execute(anyList())).thenReturn("<nmaprun/>");
        when(parser.parse(any())).thenReturn(List.of());

        assertThat(service.startScan("192.168.1.0/24").target()).isEqualTo("192.168.1.0/24");
    }

    @Test
    @DisplayName("um target publico e recusado antes de qualquer processo arrancar")
    void rejectsPublicTargetsWithoutRunningNmap() {
        assertThatThrownBy(() -> service.startScan("8.8.8.8"))
                .isInstanceOf(InvalidTargetException.class);

        verify(executor, never()).execute(anyList());
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    @DisplayName("com portas abertas, corre descoberta e depois deteccao de versao, e faz o merge")
    void runsATwoPhaseScanWhenPortsAreFound() {
        Host discovered = new Host("192.168.1.10", "nas.lan", "Linux", 94,
                List.of(new Port(22, "tcp", "open", null, null, null)));
        Host versioned = new Host("192.168.1.10", "nas.lan", "Linux", 94,
                List.of(new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6")));

        when(executor.execute(anyList())).thenReturn("discovery-xml", "version-xml");
        when(parser.parse("discovery-xml")).thenReturn(List.of(discovered));
        when(parser.parse("version-xml")).thenReturn(List.of(versioned));

        ScanJob started = service.startScan("192.168.1.0/24");
        ScanJob finished = store.find(started.id()).orElseThrow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.forClass(List.class);
        verify(executor, times(2)).execute(commands.capture());
        assertThat(commands.getAllValues().get(0)).doesNotContain("-sV");
        assertThat(commands.getAllValues().get(1)).contains("-sT", "-sV");

        assertThat(finished.hosts().get(0).ports().get(0).service()).isEqualTo("ssh");
        assertThat(finished.hosts().get(0).ports().get(0).product()).isEqualTo("OpenSSH");
    }

    @Test
    @DisplayName("se so a deteccao de versao falhar, o scan fica DONE com portas+OS mas sem servico")
    void keepsDiscoveryResultsWhenVersionDetectionFails() {
        Host discovered = new Host("192.168.1.10", "nas.lan", "Linux", 94,
                List.of(new Port(22, "tcp", "open", null, null, null)));

        when(executor.execute(anyList()))
                .thenReturn("discovery-xml")
                .thenThrow(new NmapExecutionException("falha a inquirir versao"));
        when(parser.parse("discovery-xml")).thenReturn(List.of(discovered));

        ScanJob started = service.startScan("192.168.1.0/24");
        ScanJob finished = store.find(started.id()).orElseThrow();

        assertThat(finished.status()).isEqualTo(ScanStatus.DONE);
        assertThat(finished.hosts()).extracting(Host::ip, Host::ports)
                .containsExactly(tuple(discovered.ip(), discovered.ports()));
    }

    @Test
    @DisplayName("sem hosts nem portas, nao ha segunda invocacao ao nmap")
    void skipsVersionDetectionWhenThereIsNothingToQuery() {
        when(executor.execute(anyList())).thenReturn("discovery-xml");
        when(parser.parse("discovery-xml")).thenReturn(List.of());

        service.startScan("192.168.1.0/24");

        verify(executor, times(1)).execute(anyList());
    }

    @Test
    void listsScansMostRecentFirst() {
        when(executor.execute(anyList())).thenReturn("<nmaprun/>");
        when(parser.parse(any())).thenReturn(List.of());

        service.startScan("192.168.1.0/24");
        service.startScan("10.0.0.0/24");

        assertThat(store.findAll()).hasSize(2);
    }
}
