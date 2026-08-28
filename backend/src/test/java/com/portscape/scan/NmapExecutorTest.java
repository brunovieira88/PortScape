package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.portscape.config.NmapProperties;
import com.portscape.scan.exception.NmapExecutionException;
import com.portscape.scan.exception.NmapNotFoundException;
import com.portscape.scan.exception.NmapPrivilegeException;

/**
 * Testa o executor com processos de shell a fazer de nmap. Nao corre scans reais:
 * o que interessa aqui e o tratamento do processo (saidas, streams, timeout).
 */
@DisabledOnOs(OS.WINDOWS)
class NmapExecutorTest {

    private static NmapExecutor executorWithTimeout(Duration timeout) {
        return new NmapExecutor(new NmapProperties(
                List.of("/opt/homebrew/bin/nmap"), "192.168.1.0/24", List.of(),
                timeout, Duration.ofSeconds(60)));
    }

    private static NmapExecutor executor() {
        return executorWithTimeout(Duration.ofSeconds(10));
    }

    private static List<String> shell(String script) {
        return List.of("/bin/sh", "-c", script);
    }

    @Test
    void returnsStdoutOnSuccess() {
        String output = executor().execute(shell("echo '<nmaprun/>'"));

        assertThat(output).contains("<nmaprun/>");
    }

    @Test
    @DisplayName("stdout e stderr sao lidos em paralelo: muito output nao bloqueia")
    void doesNotDeadlockOnLargeOutput() {
        // Bem acima do buffer de um pipe (64 KB): se os streams fossem lidos em
        // sequencia depois do waitFor, isto ficava preso para sempre.
        String script = "for i in $(seq 1 5000); do echo 'linha de output do nmap para encher o pipe'; "
                + "echo 'aviso no stderr' >&2; done";

        String output = executorWithTimeout(Duration.ofSeconds(30)).execute(shell(script));

        assertThat(output.lines()).hasSize(5000);
    }

    @Test
    void mapsPrivilegeErrorsToNmapPrivilegeException() {
        String script = "echo 'You requested a scan type which requires root privileges.' >&2; exit 1";

        assertThatThrownBy(() -> executor().execute(shell(script)))
                .isInstanceOf(NmapPrivilegeException.class)
                .hasMessageContaining("sudoers")
                .hasMessageContaining("requires root privileges");
    }

    @Test
    void mapsOtherFailuresToNmapExecutionException() {
        String script = "echo 'Failed to resolve target' >&2; exit 2";

        assertThatThrownBy(() -> executor().execute(shell(script)))
                .isInstanceOf(NmapExecutionException.class)
                .isNotInstanceOf(NmapPrivilegeException.class)
                .hasMessageContaining("codigo 2")
                .hasMessageContaining("Failed to resolve target");
    }

    @Test
    void failsWhenNmapSucceedsButProducesNoXml() {
        assertThatThrownBy(() -> executor().execute(shell("exit 0")))
                .isInstanceOf(NmapExecutionException.class)
                .hasMessageContaining("nao produziu XML");
    }

    @Test
    void killsTheProcessOnTimeout() {
        NmapExecutor executor = executorWithTimeout(Duration.ofMillis(300));

        assertThatThrownBy(() -> executor.execute(shell("sleep 30")))
                .isInstanceOf(NmapExecutionException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void reportsAMissingBinaryClearly() {
        assertThatThrownBy(() -> executor().execute(List.of("/nao/existe/nmap")))
                .isInstanceOf(NmapNotFoundException.class)
                .hasMessageContaining("portscape.nmap.command");
    }
}
