package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.domain.ScanStatus;

class InterruptedScanReaperTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");
    private static final String TARGET = "192.168.1.0/24";

    private final ScanJobStore store = new InMemoryScanJobStore();
    private final InterruptedScanReaper reaper =
            new InterruptedScanReaper(store, Clock.fixed(NOW, ZoneOffset.UTC));

    private ScanJob save(ScanJob job) {
        store.save(job);
        return job;
    }

    @Test
    @DisplayName("um scan que ficou a RUNNING num arranque anterior e fechado como falhado")
    void failsScansLeftRunningByAPreviousRun() {
        ScanJob interrupted = save(ScanJob.pending(UUID.randomUUID(), TARGET, NOW).running(NOW));

        reaper.run(null);

        assertThat(store.find(interrupted.id())).get()
                .extracting(ScanJob::status, ScanJob::errorCode)
                .containsExactly(ScanStatus.FAILED, InterruptedScanReaper.ERROR_CODE);
    }

    @Test
    @DisplayName("um scan que nunca chegou a arrancar tambem nao pode ficar em PENDING para sempre")
    void failsScansLeftPending() {
        ScanJob queued = save(ScanJob.pending(UUID.randomUUID(), TARGET, NOW));

        reaper.run(null);

        assertThat(store.find(queued.id())).get()
                .extracting(ScanJob::status).isEqualTo(ScanStatus.FAILED);
    }

    @Test
    @DisplayName("scans ja terminados nao sao tocados -- o historico fica como estava")
    void leavesFinishedScansAlone() {
        ScanJob done = save(ScanJob.pending(UUID.randomUUID(), TARGET, NOW)
                .running(NOW).done(List.of(), NOW));
        ScanJob failed = save(ScanJob.pending(UUID.randomUUID(), TARGET, NOW)
                .running(NOW).failed("NMAP_PRIVILEGE", "precisa de root", NOW));

        reaper.run(null);

        assertThat(store.find(done.id())).get()
                .extracting(ScanJob::status).isEqualTo(ScanStatus.DONE);
        assertThat(store.find(failed.id())).get()
                .extracting(ScanJob::errorCode).isEqualTo("NMAP_PRIVILEGE");
    }
}
