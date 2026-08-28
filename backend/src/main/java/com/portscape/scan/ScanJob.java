package com.portscape.scan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.portscape.domain.Host;
import com.portscape.domain.ScanStatus;

/**
 * Estado de um scan ao longo do seu ciclo de vida.
 *
 * <p>Imutavel de proposito: as transicoes criam uma nova instancia que substitui a
 * anterior no {@link ScanJobStore}. Assim o thread do scan e os pedidos HTTP de
 * polling nunca observam um job meio-atualizado, sem precisar de locks.
 */
public record ScanJob(
        UUID id,
        String target,
        ScanStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<Host> hosts,
        String errorCode,
        String errorMessage
) {
    public ScanJob {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
    }

    public static ScanJob pending(UUID id, String target, Instant now) {
        return new ScanJob(id, target, ScanStatus.PENDING, now, null, null, List.of(), null, null);
    }

    public ScanJob running(Instant now) {
        return new ScanJob(id, target, ScanStatus.RUNNING, createdAt, now, null, List.of(), null, null);
    }

    public ScanJob done(List<Host> found, Instant now) {
        return new ScanJob(id, target, ScanStatus.DONE, createdAt, startedAt, now, found, null, null);
    }

    public ScanJob failed(String code, String message, Instant now) {
        return new ScanJob(id, target, ScanStatus.FAILED, createdAt, startedAt, now, List.of(), code, message);
    }

    /** Duracao do scan em ms, ou null enquanto nao tiver terminado. */
    public Long durationMs() {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
