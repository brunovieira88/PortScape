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
        boolean cveLookupDegraded,
        int progress
) {
    public ScanJob {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
    }

    public static ScanJob pending(UUID id, String target, Instant now) {
        return new ScanJob(id, target, ScanStatus.PENDING, now, null, null, List.of(), null, null, false, 0);
    }

    public ScanJob running(Instant now) {
        return new ScanJob(id, target, ScanStatus.RUNNING, createdAt, now, null, List.of(), null, null, false, 0);
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
                found, null, null, cveLookupDegraded, 100);
    }

    /**
     * Parado a pedido do utilizador.
     *
     * <p>Sem {@code errorCode}: cancelar nao e falhar, e o historico nao deve marcar
     * como erro uma decisao de quem esta a usar a ferramenta. Sem hosts, tambem: o
     * nmap so produz o XML no fim, portanto matar o processo a meio nao deixa nada de
     * aproveitavel -- um scan cancelado nao tem resultados parciais para mostrar.
     */
    public ScanJob cancelled(Instant now) {
        return new ScanJob(id, target, ScanStatus.CANCELLED, createdAt, startedAt, now,
                List.of(), null, null, false, 0);
    }

    public ScanJob failed(String code, String message, Instant now) {
        return new ScanJob(id, target, ScanStatus.FAILED, createdAt, startedAt, now,
                List.of(), code, message, false, 0);
    }

    /** Duracao do scan em ms, ou null enquanto nao tiver terminado. */
    public Long durationMs() {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    public ScanJob withProgress(int newProgress) {
        return new ScanJob(id, target, status, createdAt, startedAt, finishedAt, hosts, errorCode, errorMessage, cveLookupDegraded, newProgress);
    }
}
