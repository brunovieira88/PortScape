package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.config.NmapProperties;

class NmapCommandBuilderTest {

    private static NmapCommandBuilder builderWith(List<String> command, List<String> arguments) {
        return new NmapCommandBuilder(new NmapProperties(
                command, "192.168.1.0/24", arguments, Duration.ofMinutes(10), Duration.ofSeconds(60)));
    }

    @Test
    void buildsTheFullDiscoveryCommandLine() {
        NmapCommandBuilder builder = builderWith(List.of("/usr/bin/nmap"), List.of("-sS", "-O", "--open"));

        assertThat(builder.buildDiscovery("192.168.1.0/24")).containsExactly(
                "/usr/bin/nmap", "-sS", "-O", "--open",
                "--host-timeout", "60s",
                "-oX", "-",
                "192.168.1.0/24");
    }

    @Test
    void discoveryKeepsTheCommandPrefixSoScansCanRunUnderSudo() {
        NmapCommandBuilder builder = builderWith(List.of("sudo", "-n", "/usr/bin/nmap"), List.of("-sS"));

        assertThat(builder.buildDiscovery("10.0.0.0/24")).startsWith("sudo", "-n", "/usr/bin/nmap", "-sS");
    }

    @Test
    void discoveryPutsTheTargetLast() {
        NmapCommandBuilder builder = builderWith(List.of("/usr/bin/nmap"), List.of("-sS"));

        List<String> command = builder.buildDiscovery("10.0.0.0/24");
        assertThat(command.get(command.size() - 1)).isEqualTo("10.0.0.0/24");
    }

    @Test
    void discoveryAlwaysRequestsXmlOnStdout() {
        NmapCommandBuilder builder = builderWith(List.of("/usr/bin/nmap"), List.of());

        assertThat(builder.buildDiscovery("10.0.0.0/24")).containsSequence("-oX", "-");
    }

    @Test
    @DisplayName("deteccao de versao usa -sT -sV fixo, com as portas em -p")
    void buildsTheVersionDetectionCommandLine() {
        NmapCommandBuilder builder = builderWith(List.of("/usr/bin/nmap"), List.of("-sS", "-O"));

        assertThat(builder.buildVersionDetection(List.of("192.168.1.10", "192.168.1.11"), List.of(22, 80)))
                .containsExactly(
                        "/usr/bin/nmap", "-sT", "-sV", "--open",
                        "--host-timeout", "60s",
                        "-p", "22,80",
                        "-oX", "-",
                        "192.168.1.10", "192.168.1.11");
    }

    @Test
    @DisplayName("deteccao de versao ignora o prefixo sudo -- correr como root e o que despoleta o bug")
    void versionDetectionNeverUsesSudo() {
        NmapCommandBuilder builder = builderWith(List.of("sudo", "-n", "/usr/bin/nmap"), List.of("-sS"));

        assertThat(builder.buildVersionDetection(List.of("192.168.1.10"), List.of(443)))
                .startsWith("/usr/bin/nmap")
                .doesNotContain("sudo");
    }
}
