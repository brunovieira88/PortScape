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
        String errorMessage,
        boolean cveLookupDegraded
) {
    public ScanJob {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
    }

    /** Construtor da fase 1, sem a flag de degradacao -- usado por testes e leitura antiga. */
    public ScanJob(UUID id, String target, ScanStatus status, Instant createdAt, Instant startedAt,
                   Instant finishedAt, List<Host> hosts, String errorCode, String errorMessage) {
        this(id, target, status, createdAt, startedAt, finishedAt, hosts, errorCode, errorMessage, false);
    }

    public static ScanJob pending(UUID id, String target, Instant now) {
        return new ScanJob(id, target, ScanStatus.PENDING, now, null, null, List.of(), null, null, false);
    }

    public ScanJob running(Instant now) {
        return new ScanJob(id, target, ScanStatus.RUNNING, createdAt, now, null, List.of(), null, null, false);
    }

    public ScanJob done(List<Host> found, Instant now) {
        return done(found, now, false);
    }

    /**
     * @param cveLookupDegraded pelo menos uma consulta ao NVD falhou, por isso os
     *                          scores estao incompletos. O cliente precisa de saber
     *                          para nao ler "sem CVEs" como "sem problemas"
     */
    public ScanJob done(List<Host> found, Instant now, boolean cveLookupDegraded) {
        return new ScanJob(id, target, ScanStatus.DONE, createdAt, startedAt, now,
                found, null, null, cveLookupDegraded);
    }

    public ScanJob failed(String code, String message, Instant now) {
        return new ScanJob(id, target, ScanStatus.FAILED, createdAt, startedAt, now,
                List.of(), code, message, false);
    }

    /** Duracao do scan em ms, ou null enquanto nao tiver terminado. */
    public Long durationMs() {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }
}
