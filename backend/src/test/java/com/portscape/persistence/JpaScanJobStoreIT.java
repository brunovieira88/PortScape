package com.portscape.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.domain.ScanStatus;
import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanJobStore;

@SpringBootTest
class JpaScanJobStoreIT extends PostgresTestBase {

    @Autowired
    private ScanJobStore store;
    @Autowired
    private ScanRepository repository;

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private static ScanJob pending(String target, Instant createdAt) {
        return ScanJob.pending(UUID.randomUUID(), target, createdAt);
    }

    @Test
    @DisplayName("um scan concluido sobrevive intacto a ida e volta a base de dados")
    void roundTripsACompletedScan() {
        Host host = new Host("192.168.1.1", "router.lan", "Linux 5.4 - 5.15", 94, List.of(
                new Port(23, "tcp", "open", "telnet", "BusyBox telnetd", null),
                new Port(80, "tcp", "open", "http", "lighttpd", "1.4.59")));
        ScanJob job = pending("192.168.1.0/24", NOW).running(NOW).done(List.of(host), NOW);

        store.save(job);
        ScanJob reloaded = store.find(job.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(ScanStatus.DONE);
        assertThat(reloaded.target()).isEqualTo("192.168.1.0/24");
        assertThat(reloaded.startedAt()).isEqualTo(NOW);
        assertThat(reloaded.finishedAt()).isEqualTo(NOW);
        assertThat(reloaded.hosts()).containsExactly(host);
    }

    @Test
    @DisplayName("as transicoes do job sobrepoem-se em vez de acumular hosts orfaos")
    void overwritesHostsOnEachTransition() {
        ScanJob job = pending("192.168.1.0/24", NOW);
        store.save(job);
        store.save(job = job.running(NOW));

        store.save(job.done(List.of(new Host("192.168.1.5", null, null, null,
                List.of(new Port(22, "tcp", "open", "ssh", null, null)))), NOW));
        store.save(job.done(List.of(new Host("192.168.1.6", null, null, null, List.of())), NOW));

        assertThat(store.find(job.id()).orElseThrow().hosts())
                .singleElement()
                .extracting(Host::ip).isEqualTo("192.168.1.6");
    }

    @Test
    void persistsFailureDetails() {
        ScanJob job = pending("10.0.0.0/24", NOW).running(NOW)
                .failed("NMAP_PRIVILEGE", "precisa de root", NOW);

        store.save(job);
        ScanJob reloaded = store.find(job.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(ScanStatus.FAILED);
        assertThat(reloaded.errorCode()).isEqualTo("NMAP_PRIVILEGE");
        assertThat(reloaded.errorMessage()).isEqualTo("precisa de root");
        assertThat(reloaded.hosts()).isEmpty();
    }

    @Test
    void listsScansMostRecentFirst() {
        store.save(pending("10.0.0.0/24", NOW.minusSeconds(60)));
        store.save(pending("192.168.1.0/24", NOW));

        assertThat(store.findAll()).extracting(ScanJob::target)
                .containsExactly("192.168.1.0/24", "10.0.0.0/24");
    }

    @Test
    void returnsEmptyForAnUnknownId() {
        assertThat(store.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("o progresso avanca mas nunca recua, mesmo com atualizacoes fora de ordem")
    void onlyEverMovesProgressForward() {
        ScanJob running = pending("192.168.1.0/24", NOW).running(NOW);
        store.save(running);

        store.updateProgress(running.id(), 40);
        store.updateProgress(running.id(), 12);

        assertThat(store.find(running.id())).get()
                .extracting(ScanJob::progress).isEqualTo(40);
    }

    @Test
    @DisplayName("uma atualizacao atrasada nao mexe num scan ja terminado")
    void ignoresProgressForAScanThatAlreadyFinished() {
        ScanJob done = pending("192.168.1.0/24", NOW).running(NOW).done(List.of(), NOW);
        store.save(done);

        // O thread que le o pipe do nmap chega atrasado, depois do estado final gravado.
        store.updateProgress(done.id(), 87);

        assertThat(store.find(done.id())).get()
                .extracting(ScanJob::status, ScanJob::progress)
                .containsExactly(ScanStatus.DONE, 100);
    }

    @Test
    @DisplayName("um scan que ficou a meio e encontrado como por terminar")
    void findsUnfinishedScans() {
        ScanJob interrupted = pending("192.168.1.0/24", NOW).running(NOW);
        ScanJob finished = pending("10.0.0.0/24", NOW).running(NOW).done(List.of(), NOW);
        store.save(interrupted);
        store.save(finished);

        assertThat(store.findUnfinished()).extracting(ScanJob::id)
                .containsExactly(interrupted.id());
    }
}
