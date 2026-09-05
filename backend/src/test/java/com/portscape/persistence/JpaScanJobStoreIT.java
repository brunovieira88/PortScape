package com.portscape.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
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
import com.portscape.risk.kev.KevListing;
import com.portscape.risk.nvd.Cve;
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
    @DisplayName("os CVEs de uma porta sobrevivem com a ordem, o total e a listagem KEV")
    void roundTripsThePortCves() {
        // A ordem e escolhida pelo enricher (pior CVSS primeiro) e nao e reproduzivel
        // ordenando na leitura -- o CVE sem score nao tem por onde desempatar. E o que
        // o @OrderColumn existe para garantir.
        // Vector CVSS v4.0 tal como o NVD o emite -- 174 caracteres, porque a API
        // escreve todas as metricas opcionais como ":X". A coluna comecou por ser
        // VARCHAR(128) e um scan real a um produto com metricas v4.0 rebentava com
        // "value too long"; e este valor que impede a regressao.
        Cve worst = new Cve("CVE-2024-6387", 8.1, "HIGH",
                "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:N/VI:N/VA:N/SC:N/SI:L/SA:N/E:X/CR:X/IR:X"
                        + "/AR:X/MAV:X/MAC:X/MAT:X/MPR:X/MUI:X/MVC:X/MVI:X/MVA:X/MSC:X/MSI:X"
                        + "/MSA:X/S:X/AU:X/R:X/V:X/RE:X/U:X",
                Instant.parse("2024-07-01T13:15:00Z"), "race condition no sshd",
                new KevListing(LocalDate.of(2024, 7, 8), true,
                        "OpenSSH Signal Handler Race Condition", "Apply updates."));
        Cve unscored = new Cve("CVE-2023-51385", null, null, null, null, "sem metricas");

        // 2 guardados de 431 encontrados: e o caso do tecto, e o total tem de o dizer.
        Port ssh = new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6",
                List.of("cpe:/a:openbsd:openssh:9.6"), List.of(worst, unscored), 431);
        Host host = new Host("192.168.1.1", "router.lan", "Linux 5.4 - 5.15", 94, List.of(ssh));
        ScanJob job = pending("192.168.1.0/24", NOW).running(NOW).done(List.of(host), NOW);

        store.save(job);
        Port reloaded = store.find(job.id()).orElseThrow().hosts().get(0).ports().get(0);

        assertThat(reloaded.cves()).containsExactly(worst, unscored);
        assertThat(reloaded.cveTotal()).isEqualTo(431);
        // Um CVE que nao consta do catalogo nao pode voltar da BD a dizer que consta.
        assertThat(reloaded.cves().get(1).kev()).isNull();
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
