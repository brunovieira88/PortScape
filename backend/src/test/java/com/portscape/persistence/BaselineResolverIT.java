package com.portscape.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.portscape.baseline.BaselineNotAllowedException;
import com.portscape.baseline.BaselineResolver;
import com.portscape.baseline.BaselineSnapshot;
import com.portscape.baseline.BaselineService;
import com.portscape.baseline.HostChange;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanJobStore;

@SpringBootTest
@Import(BaselineResolverIT.FixedClock.class)
class BaselineResolverIT extends PostgresTestBase {

    /**
     * O relogio tem de estar fixo aqui.
     *
     * <p>O inventario e uma janela de tempo contada a partir de agora, e os scans deste
     * teste tem datas fixas: com o relogio do sistema, os testes passavam enquanto a
     * data real estivesse a menos de uma janela do NOW e comecavam a falhar sozinhos
     * uns dias depois, sem ninguem ter mexido em codigo nenhum.
     */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

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

    private static Host device(String mac, String ip) {
        return new Host(ip, mac, "Example Networks", null, null, null, List.of(), null);
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
                .extracting(BaselineSnapshot::scanId).isEqualTo(recent.id());
    }

    @Test
    @DisplayName("um scan fixado ganha ao scan anterior")
    void prefersThePinnedBaseline() {
        ScanJob old = doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80, 23));
        baselineService.pin(old.id());

        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(old.id());
    }

    @Test
    @DisplayName("desafixar volta ao comportamento implicito")
    void unpinningRestoresTheImplicitBaseline() {
        ScanJob old = doneScan(TARGET, NOW.minusSeconds(600));
        ScanJob recent = doneScan(TARGET, NOW.minusSeconds(60));
        baselineService.pin(old.id());

        assertThat(baselineService.unpin(TARGET)).isTrue();
        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(recent.id());
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

        assertThat(resolver.resolveFor(only)).isEmpty();
    }

    @Test
    @DisplayName("um scan do historico compara com o que o precedeu, nao com o mais recente")
    void comparesAHistoricalScanWithTheOneBeforeIt() {
        ScanJob first = doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        ScanJob middle = doneScan(TARGET, NOW.minusSeconds(300), host("192.168.1.1", 80));
        doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80));

        // Sem o filtro pelo instante, o do meio recebia o mais recente -- um scan
        // posterior a ele -- e o diff saia invertido.
        assertThat(resolver.resolveFor(middle)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(first.id());
    }

    @Test
    @DisplayName("o scan mais recente continua a comparar com o anterior")
    void stillComparesTheNewestScanWithThePreviousOne() {
        doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        ScanJob middle = doneScan(TARGET, NOW.minusSeconds(300), host("192.168.1.1", 80));
        ScanJob newest = doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80));

        assertThat(resolver.resolveFor(newest)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(middle.id());
    }

    @Test
    @DisplayName("o primeiro scan de uma rede nao tem nada antes dele")
    void givesTheOldestScanNoBaseline() {
        ScanJob first = doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80));

        assertThat(resolver.resolveFor(first)).isEmpty();
    }

    @Test
    @DisplayName("o baseline fixado aplica-se tambem aos scans anteriores a ele")
    void appliesThePinnedBaselineToHistoricalScansToo() {
        ScanJob first = doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        ScanJob middle = doneScan(TARGET, NOW.minusSeconds(300), host("192.168.1.1", 80));
        ScanJob newest = doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80));
        baselineService.pin(newest.id());

        // Fixar e dizer "medir tudo contra isto", inclusive o que veio antes.
        assertThat(resolver.resolveFor(middle)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(newest.id());
        assertThat(resolver.resolveFor(first)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(newest.id());
    }

    @Test
    @DisplayName("nem o baseline fixado se aplica a si proprio")
    void neverPinsAScanAgainstItself() {
        ScanJob first = doneScan(TARGET, NOW.minusSeconds(600), host("192.168.1.1", 80));
        ScanJob pinned = doneScan(TARGET, NOW.minusSeconds(60), host("192.168.1.1", 80));
        baselineService.pin(pinned.id());

        assertThat(resolver.resolveFor(pinned)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(first.id());
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

    @Test
    @DisplayName("um dispositivo offline ha varios scans continua no inventario")
    void keepsADeviceThatHasBeenGoneForSeveralScans() {
        doneScan(TARGET, NOW.minus(Duration.ofDays(4)), host("192.168.1.1", 80), host("192.168.1.9", 22));
        doneScan(TARGET, NOW.minus(Duration.ofDays(3)), host("192.168.1.1", 80));
        doneScan(TARGET, NOW.minus(Duration.ofDays(2)), host("192.168.1.1", 80));

        // Com o baseline a ser so o scan anterior, o .9 desaparecia da cidade logo no
        // segundo scan -- porque tambem ja nao estava no anterior. E era isso que fazia
        // uma maquina desligada ontem ser indistinguivel de uma que nunca existiu.
        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(BaselineSnapshot::hosts, list(Host.class))
                .extracting(Host::ip)
                .containsExactlyInAnyOrder("192.168.1.1", "192.168.1.9");
    }

    @Test
    @DisplayName("passada a janela, o dispositivo sai do inventario")
    void forgetsADeviceThatHasNotBeenSeenInsideTheWindow() {
        doneScan(TARGET, NOW.minus(Duration.ofDays(9)), host("192.168.1.9", 22));
        doneScan(TARGET, NOW.minus(Duration.ofDays(1)), host("192.168.1.1", 80));

        // O telemovel de uma visita nao pode ficar na cidade para sempre.
        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(BaselineSnapshot::hosts, list(Host.class))
                .extracting(Host::ip)
                .containsExactly("192.168.1.1");
    }

    @Test
    @DisplayName("de cada dispositivo fica o estado mais recente, nao o primeiro")
    void keepsTheMostRecentStateOfEachDevice() {
        doneScan(TARGET, NOW.minus(Duration.ofDays(3)), host("192.168.1.1", 80));
        doneScan(TARGET, NOW.minus(Duration.ofDays(1)), host("192.168.1.1", 80, 443));

        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(BaselineSnapshot::hosts, list(Host.class))
                .singleElement()
                .extracting(h -> h.ports().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("o inventario segue o dispositivo quando ele muda de IP")
    void followsADeviceThroughAnAddressChange() {
        doneScan(TARGET, NOW.minus(Duration.ofDays(3)), device("AA:BB:CC:00:11:22", "192.168.1.68"));
        doneScan(TARGET, NOW.minus(Duration.ofDays(1)), device("AA:BB:CC:00:11:22", "192.168.1.70"));

        // Um so dispositivo, no ultimo endereco conhecido -- e nao dois, com o .68 a
        // ficar la para sempre como uma ruina que nunca existiu.
        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(BaselineSnapshot::hosts, list(Host.class))
                .singleElement()
                .extracting(Host::ip).isEqualTo("192.168.1.70");
    }

    @Test
    @DisplayName("um scan do historico ve o inventario tal como estava antes dele")
    void givesAHistoricalScanTheInventoryOfItsOwnTime() {
        doneScan(TARGET, NOW.minus(Duration.ofDays(5)), host("192.168.1.9", 22));
        ScanJob meio = doneScan(TARGET, NOW.minus(Duration.ofDays(3)), host("192.168.1.1", 80));
        doneScan(TARGET, NOW.minus(Duration.ofDays(1)), host("192.168.1.50", 22));

        // O .50 so apareceu depois: nao pode fazer parte do que se sabia na altura.
        assertThat(resolver.resolveFor(meio)).get()
                .extracting(BaselineSnapshot::hosts, list(Host.class))
                .extracting(Host::ip)
                .containsExactly("192.168.1.9");
    }

    @Test
    @DisplayName("o baseline fixado ganha ao inventario")
    void aPinnedBaselineStillWinsOverTheInventory() {
        ScanJob fixado = doneScan(TARGET, NOW.minus(Duration.ofDays(5)), host("192.168.1.9", 22));
        doneScan(TARGET, NOW.minus(Duration.ofDays(1)), host("192.168.1.1", 80));
        baselineService.pin(fixado.id());

        assertThat(resolver.resolveFor(TARGET)).get()
                .extracting(BaselineSnapshot::scanId).isEqualTo(fixado.id());
    }
}
