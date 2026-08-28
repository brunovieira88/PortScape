package com.portscape.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.ScanJob;

class ScanDifferTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");
    private static final UUID BASELINE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static Host host(String ip, int... ports) {
        return new Host(ip, null, null, null, java.util.Arrays.stream(ports)
                .mapToObj(port -> new Port(port, "tcp", "open", null, null, null))
                .toList());
    }

    private static ScanJob scan(UUID id, Host... hosts) {
        return ScanJob.pending(id, "192.168.1.0/24", NOW).running(NOW).done(List.of(hosts), NOW);
    }

    private static ScanDiff diff(List<Host> current, List<Host> baseline) {
        return ScanDiffer.diff(
                scan(UUID.randomUUID(), current.toArray(Host[]::new)),
                scan(BASELINE_ID, baseline.toArray(Host[]::new)));
    }

    @Test
    void marksAHostThatIsNotInTheBaselineAsNew() {
        ScanDiff result = diff(List.of(host("192.168.1.1", 80), host("192.168.1.99", 22)),
                List.of(host("192.168.1.1", 80)));

        assertThat(result.changeFor("192.168.1.99")).isEqualTo(HostChange.NEW);
        assertThat(result.changeFor("192.168.1.1")).isEqualTo(HostChange.UNCHANGED);
    }

    @Test
    void marksAHostWithANewOpenPortAsChanged() {
        ScanDiff result = diff(List.of(host("192.168.1.1", 80, 3389)),
                List.of(host("192.168.1.1", 80)));

        assertThat(result.changeFor("192.168.1.1")).isEqualTo(HostChange.CHANGED);
    }

    @Test
    @DisplayName("uma porta que fechou tambem e uma mudanca")
    void marksAHostWithAClosedPortAsChanged() {
        ScanDiff result = diff(List.of(host("192.168.1.1", 80)),
                List.of(host("192.168.1.1", 80, 23)));

        assertThat(result.changeFor("192.168.1.1")).isEqualTo(HostChange.CHANGED);
    }

    @Test
    @DisplayName("a ordem das portas nao e uma mudanca")
    void ignoresPortOrdering() {
        ScanDiff result = diff(List.of(host("192.168.1.1", 443, 80)),
                List.of(host("192.168.1.1", 80, 443)));

        assertThat(result.changeFor("192.168.1.1")).isEqualTo(HostChange.UNCHANGED);
    }

    @Test
    void marksAHostWhoseOsChangedAsChanged() {
        Host before = new Host("192.168.1.1", null, "Linux 4.X", 90, List.of());
        Host after = new Host("192.168.1.1", null, "Linux 5.X", 94, List.of());

        assertThat(diff(List.of(after), List.of(before)).changeFor("192.168.1.1"))
                .isEqualTo(HostChange.CHANGED);
    }

    @Test
    @DisplayName("uma versao de servico diferente nao conta: o nmap acerta-a de forma intermitente")
    void doesNotTreatAServiceVersionChangeAsAChange() {
        Host before = new Host("192.168.1.1", null, null, null,
                List.of(new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.5")));
        Host after = new Host("192.168.1.1", null, null, null,
                List.of(new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6")));

        assertThat(diff(List.of(after), List.of(before)).changeFor("192.168.1.1"))
                .isEqualTo(HostChange.UNCHANGED);
    }

    @Test
    @DisplayName("um host que deixou de responder aparece em 'disappeared', nao desaparece do relatorio")
    void reportsHostsThatVanished() {
        ScanDiff result = diff(List.of(host("192.168.1.1", 80)),
                List.of(host("192.168.1.1", 80), host("192.168.1.42", 22)));

        assertThat(result.disappeared()).extracting(Host::ip).containsExactly("192.168.1.42");
    }

    @Test
    void twoIdenticalScansHaveNoChangesAtAll() {
        List<Host> hosts = List.of(host("192.168.1.1", 80), host("192.168.1.2", 22));

        ScanDiff result = diff(hosts, hosts);

        assertThat(result.changeByIp().values()).containsOnly(HostChange.UNCHANGED);
        assertThat(result.disappeared()).isEmpty();
    }

    @Test
    @DisplayName("sem baseline nada e novo nem alterado -- e UNKNOWN, que nao e o mesmo que UNCHANGED")
    void reportsUnknownWhenThereIsNoBaseline() {
        ScanDiff result = ScanDiffer.diff(scan(UUID.randomUUID(), host("192.168.1.1", 23)), null);

        assertThat(result.hasBaseline()).isFalse();
        assertThat(result.changeFor("192.168.1.1")).isEqualTo(HostChange.UNKNOWN);
    }

    @Test
    void namesTheBaselineItComparedAgainst() {
        assertThat(diff(List.of(host("192.168.1.1", 80)), List.of(host("192.168.1.1", 80))))
                .extracting(ScanDiff::baselineScanId).isEqualTo(BASELINE_ID);
    }
}
