package com.portscape.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.portscape.baseline.BaselineNotAllowedException;
import com.portscape.baseline.BaselineResolver;
import com.portscape.baseline.BaselineService;
import com.portscape.baseline.HostChange;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanJobStore;

@SpringBootTest
class BaselineResolverIT extends PostgresTestBase {

    private static final String TARGET = "192.168.1.0/24";
    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

    @Autowired
    private ScanJobStore store;
    @Autowired
    private BaselineResolver resolver;
    @Autowired
    private BaselineService baselineService;
    @Autowired
    private BaselineRepository baselineRepository;
    @Autowired
    private ScanRepository scanRepository;

    @BeforeEach
    void clean() {
        baselineRepository.deleteAll();
        scanRepository.deleteAll();
    }

    private ScanJob doneScan(String target, Instant finishedAt, Host... hosts) {
        ScanJob job = ScanJob.pending(UUID.randomUUID(), target, finishedAt)
                .running(finishedAt)
                .done(List.of(hosts), finishedAt);
        store.save(job);
        return job;
    }

    private static Host host(String ip, int... ports) {
        return new Host(ip, null, null, null, java.util.Arrays.stream(ports)
                .mapToObj(port -> new Port(port, "tcp", "open", null, null, null)).toList());
    }

    @Test
    @DisplayName("sem nada fixado, o baseline e o ultimo scan concluido da mesma rede")
    void fallsBackToThePreviousScan() {
        doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        ScanJob recent = doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80));

        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(ScanJob::id).isEqualTo(recent.id());
    }

    @Test
    @DisplayName("um scan fixado ganha ao scan anterior")
    void prefersThePinnedBaseline() {
        ScanJob old = doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80, 23));
        baselineService.pin(old.id());

        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(ScanJob::id).isEqualTo(old.id());
    }

    @Test
    @DisplayName("desafixar volta ao comportamento implicito")
    void unpinningRestoresTheImplicitBaseline() {
        ScanJob old = doneScan(TARGET, NOW.minusSeconds(600));
        ScanJob recent = doneScan(TARGET, NOW.minusSeconds(60));
        baselineService.pin(old.id());

        assertThat(baselineService.unpin(TARGET)).isTrue();
        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(ScanJob::id).isEqualTo(recent.id());
    }

    @Test
    @DisplayName("no primeiro scan de uma rede nao ha baseline nenhum")
    void hasNoBaselineForANetworkNeverScannedBefore() {
        assertThat(resolver.resolveFor("10.9.9.0/24")).isEmpty();
    }

    @Test
    @DisplayName("um scan nunca e o seu proprio baseline")
    void neverComparesAScanWithItself() {
        ScanJob only = doneScan(TARGET, NOW, host("192.168.1.1", 80));

        assertThat(resolver.resolveFor(TARGET, only.id())).isEmpty();
    }

    @Test
    @DisplayName("scans de outra rede nao servem de baseline")
    void doesNotBorrowABaselineFromAnotherNetwork() {
        doneScan("10.0.0.0/24", NOW, host("10.0.0.1", 80));

        assertThat(resolver.resolveFor(TARGET)).isEmpty();
    }

    @Test
    @DisplayName("um scan que falhou nao pode ser fixado: daria um baseline vazio")
    void refusesToPinAScanThatIsNotDone() {
        ScanJob failed = ScanJob.pending(UUID.randomUUID(), TARGET, NOW)
                .running(NOW).failed("NMAP_PRIVILEGE", "precisa de root", NOW);
        store.save(failed);

        assertThatThrownBy(() -> baselineService.pin(failed.id()))
                .isInstanceOf(BaselineNotAllowedException.class);
    }

    @Test
    void refusesToPinAScanThatDoesNotExist() {
        assertThatThrownBy(() -> baselineService.pin(UUID.randomUUID()))
                .isInstanceOf(BaselineNotAllowedException.class);
    }

    @Test
    @DisplayName("fixar outra vez substitui a fixacao em vez de acumular")
    void pinningAgainReplacesThePreviousPin() {
        ScanJob first = doneScan(TARGET, NOW.minusSeconds(600));
        ScanJob second = doneScan(TARGET, NOW.minusSeconds(60));

        baselineService.pin(first.id());
        baselineService.pin(second.id());

        assertThat(baselineService.findAll()).singleElement()
                .extracting(baseline -> baseline.scanId()).isEqualTo(second.id());
    }

    @Test
    void unpinningSomethingThatWasNotPinnedReportsNothingRemoved() {
        assertThat(baselineService.unpin(TARGET)).isFalse();
    }

    @Test
    @DisplayName("fluxo completo: dois scans e o diff marca o dispositivo novo")
    void computesTheDiffAgainstTheResolvedBaseline() {
        doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        ScanJob current = doneScan(TARGET, NOW,
                host("192.168.1.1", 80), host("192.168.1.99", 23));

        var diff = baselineService.diffFor(current.id()).orElseThrow();

        assertThat(diff.changeFor("192.168.1.99")).isEqualTo(HostChange.NEW);
        assertThat(diff.changeFor("192.168.1.1")).isEqualTo(HostChange.UNCHANGED);
    }
}
