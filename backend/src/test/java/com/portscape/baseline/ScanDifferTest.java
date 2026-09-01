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

    /** O mesmo dispositivo, identificado pelo MAC, no endereco que o DHCP lhe deu. */
    private static Host device(String mac, String ip, int... ports) {
        return new Host(ip, mac, "Example Networks", null, null, null,
                java.util.Arrays.stream(ports)
                        .mapToObj(port -> new Port(port, "tcp", "open", null, null, null))
                        .toList(), null);
    }

    private static Host host(String ip, int... ports) {
        return new Host(ip, null, null, null, java.util.Arrays.stream(ports)
                .mapToObj(port -> new Port(port, "tcp", "open", null, null, null))
                .toList());
    }

    private static ScanJob scan(UUID id, Host... hosts) {
        return ScanJob.pending(id, "192.168.1.0/24", NOW).running(NOW).done(List.of(hosts), NOW);
    }

    private static ScanJob scanWith(Host... hosts) {
        return scan(UUID.randomUUID(), hosts);
    }

    /** O inventario que um scan por si so representaria. */
    private static BaselineSnapshot inventoryOf(ScanJob scan) {
        return new BaselineSnapshot(scan.id(), scan.hosts());
    }

    private static ScanDiff diff(List<Host> current, List<Host> baseline) {
        return ScanDiffer.diff(
                scan(UUID.randomUUID(), current.toArray(Host[]::new)),
                inventoryOf(scan(BASELINE_ID, baseline.toArray(Host[]::new))));
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

    @Test
    @DisplayName("o mesmo dispositivo noutro IP nao e um host novo")
    void followsADeviceAcrossAnAddressChange() {
        ScanJob antes = scanWith(device("AA:BB:CC:00:11:22", "192.168.1.68", 22));
        ScanJob agora = scanWith(device("AA:BB:CC:00:11:22", "192.168.1.70", 22));

        ScanDiff diff = ScanDiffer.diff(agora, inventoryOf(antes));

        // Com a comparacao por IP isto dava NEW no .70 e o .68 como desaparecido --
        // dois falsos alarmes por cada renovacao de aluguer de DHCP.
        assertThat(diff.changeFor("192.168.1.70")).isEqualTo(HostChange.UNCHANGED);
        assertThat(diff.disappeared()).isEmpty();
    }

    @Test
    @DisplayName("um MAC nunca visto e um host novo, mesmo que reutilize um IP conhecido")
    void flagsAnUnknownDeviceEvenOnAFamiliarAddress() {
        ScanJob antes = scanWith(device("AA:BB:CC:00:11:22", "192.168.1.68", 22));
        ScanJob agora = scanWith(device("FF:EE:DD:99:88:77", "192.168.1.68", 22));

        ScanDiff diff = ScanDiffer.diff(agora, inventoryOf(antes));

        // E este o caso que interessa a uma auditoria: alguem ocupou o endereco.
        assertThat(diff.changeFor("192.168.1.68")).isEqualTo(HostChange.NEW);
        assertThat(diff.disappeared()).extracting(Host::mac).containsExactly("AA:BB:CC:00:11:22");
    }

    @Test
    @DisplayName("um dispositivo que muda de IP e de portas conta como alterado")
    void stillReportsRealChangesOnADeviceThatMoved() {
        ScanJob antes = scanWith(device("AA:BB:CC:00:11:22", "192.168.1.68", 22));
        ScanJob agora = scanWith(device("AA:BB:CC:00:11:22", "192.168.1.70", 22, 23));

        assertThat(ScanDiffer.diff(agora, inventoryOf(antes)).changeFor("192.168.1.70"))
                .isEqualTo(HostChange.CHANGED);
    }

    @Test
    @DisplayName("sem MAC vale o IP: e o caso do proprio portatil e dos scans sem privilegios")
    void fallsBackToTheAddressWhenThereIsNoMac() {
        ScanJob antes = scanWith(host("192.168.1.68", 22));
        ScanJob agora = scanWith(host("192.168.1.68", 22));

        assertThat(ScanDiffer.diff(agora, inventoryOf(antes)).changeFor("192.168.1.68"))
                .isEqualTo(HostChange.UNCHANGED);
    }

    @Test
    @DisplayName("um host com MAC e outro sem nao sao emparelhados por acaso")
    void doesNotMatchAMaclessHostAgainstADeviceIdentity() {
        ScanJob antes = scanWith(device("AA:BB:CC:00:11:22", "192.168.1.68", 22));
        ScanJob agora = scanWith(host("192.168.1.68", 22));

        // O scan novo nao conseguiu resolver o MAC, portanto o .68 identifica-se pelo
        // IP e nao bate com "AA:BB:...". E o comportamento conservador certo: sinaliza
        // em vez de assumir que e o mesmo.
        ScanDiff diff = ScanDiffer.diff(agora, inventoryOf(antes));
        assertThat(diff.changeFor("192.168.1.68")).isEqualTo(HostChange.NEW);
        assertThat(diff.disappeared()).hasSize(1);
    }
}
