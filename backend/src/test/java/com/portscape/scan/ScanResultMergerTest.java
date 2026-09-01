package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.domain.Host;
import com.portscape.domain.Port;

class ScanResultMergerTest {

    private static Host host(String ip, Port... ports) {
        return new Host(ip, "host.lan", "Linux", 90, List.of(ports));
    }

    /** Um host como a fase de descoberta o entrega: privilegiada, portanto com MAC. */
    private static Host discovered(String ip, Port... ports) {
        return new Host(ip, "AA:BB:CC:00:11:22", "Xiaomi Communications", "host.lan",
                "Linux", 90, List.of(ports), null);
    }

    @Test
    @DisplayName("preenche servico/produto/versao quando a segunda fase encontra a mesma porta")
    void fillsInServiceDetailsForMatchingPorts() {
        Host discovered = host("192.168.1.10", new Port(22, "tcp", "open", null, null, null));
        Host versioned = host("192.168.1.10", new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6"));

        List<Host> merged = ScanResultMerger.merge(List.of(discovered), List.of(versioned));

        Port port = merged.get(0).ports().get(0);
        assertThat(port.service()).isEqualTo("ssh");
        assertThat(port.product()).isEqualTo("OpenSSH");
        assertThat(port.version()).isEqualTo("9.6");
    }

    @Test
    @DisplayName("porta/estado da descoberta nunca sao substituidos pela segunda fase")
    void keepsDiscoveryAsTheSourceOfTruthForPortAndState() {
        Host discovered = host("192.168.1.10", new Port(22, "tcp", "open", null, null, null));
        // Hipotetico: se a segunda fase reportasse a porta com outro estado, ignora-se.
        Host versioned = host("192.168.1.10", new Port(22, "udp", "closed", "ssh", "OpenSSH", "9.6"));

        Port port = ScanResultMerger.merge(List.of(discovered), List.of(versioned)).get(0).ports().get(0);

        assertThat(port.protocol()).isEqualTo("tcp");
        assertThat(port.state()).isEqualTo("open");
        assertThat(port.service()).isEqualTo("ssh");
    }

    @Test
    @DisplayName("host que a segunda fase nao encontrou fica exatamente como a descoberta")
    void keepsHostsMissingFromVersionInfoUnchanged() {
        Host discovered = host("192.168.1.10", new Port(22, "tcp", "open", null, null, null));

        List<Host> merged = ScanResultMerger.merge(List.of(discovered), List.of());

        assertThat(merged).containsExactly(discovered);
    }

    @Test
    @DisplayName("porta que a segunda fase nao encontrou fica sem servico, nao desaparece")
    void keepsPortsMissingFromVersionInfoUnchanged() {
        Host discovered = host("192.168.1.10",
                new Port(22, "tcp", "open", null, null, null),
                new Port(80, "tcp", "open", null, null, null));
        Host versioned = host("192.168.1.10", new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6"));

        List<Port> ports = ScanResultMerger.merge(List.of(discovered), List.of(versioned)).get(0).ports();

        assertThat(ports).extracting(Port::number).containsExactly(22, 80);
        assertThat(ports.get(1).service()).isNull();
    }

    @Test
    void preservesOsInfoFromDiscoveryUnchanged() {
        Host discovered = host("192.168.1.10", new Port(22, "tcp", "open", null, null, null));
        Host versioned = host("192.168.1.10", new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6"));

        Host merged = ScanResultMerger.merge(List.of(discovered), List.of(versioned)).get(0);

        assertThat(merged.osGuess()).isEqualTo("Linux");
        assertThat(merged.osAccuracy()).isEqualTo(90);
    }

    @Test
    void handlesEmptyInputs() {
        assertThat(ScanResultMerger.merge(List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("a fase 2 nao apaga o servico da descoberta quando nao identifica nada")
    void keepsTheDiscoveryServiceWhenVersionDetectionFindsNothing() {
        List<Host> discovered = List.of(host("192.168.1.1", new Port(22, "tcp", "open", "ssh", null, null)));
        List<Host> versionInfo = List.of(host("192.168.1.1", new Port(22, "tcp", "open", null, null, null)));

        Port merged = ScanResultMerger.merge(discovered, versionInfo).get(0).ports().get(0);

        assertThat(merged.service()).isEqualTo("ssh");
    }

    @Test
    @DisplayName("tcpwrapped nao e um servico: vale menos que o palpite da descoberta")
    void doesNotLetTcpwrappedOverwriteAKnownService() {
        List<Host> discovered = List.of(host("192.168.1.1", new Port(22, "tcp", "open", "ssh", null, null)));
        List<Host> versionInfo = List.of(host("192.168.1.1", new Port(22, "tcp", "open", "tcpwrapped", null, null)));

        Port merged = ScanResultMerger.merge(discovered, versionInfo).get(0).ports().get(0);

        assertThat(merged.service()).isEqualTo("ssh");
    }

    @Test
    @DisplayName("o MAC e o fabricante sobrevivem a segunda fase, que nao os tem")
    void keepsTheHardwareAddressThatOnlyDiscoveryCanSee() {
        // A regressao: a copia do host era escrita a mao com o construtor curto, que
        // poe mac e vendor a null. Como a segunda fase toca em todos os hosts com
        // portas abertas, isso apagava o fabricante da rede inteira -- e com ele a
        // identidade entre scans e a forma do edificio na cena.
        Host discovered = discovered("192.168.1.73", new Port(22, "tcp", "open", null, null, null));
        Host versioned = host("192.168.1.73", new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6"));

        Host merged = ScanResultMerger.merge(List.of(discovered), List.of(versioned)).get(0);

        assertThat(merged.mac()).isEqualTo("AA:BB:CC:00:11:22");
        assertThat(merged.vendor()).isEqualTo("Xiaomi Communications");
        assertThat(merged.identity()).isEqualTo("AA:BB:CC:00:11:22");
        // E continua a ganhar o que a segunda fase tinha a acrescentar.
        assertThat(merged.ports().get(0).product()).isEqualTo("OpenSSH");
    }
}
