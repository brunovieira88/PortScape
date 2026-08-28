package com.portscape.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portscape.baseline.ScanDiff;
import com.portscape.domain.ScanStatus;
import com.portscape.scan.ScanJob;

/**
 * Envelope canonico de um scan.
 *
 * <p>E o formato que o resto do projeto vai assumir: a fase 2 acrescenta-lhe o risco,
 * a fase 3 a posicao 3D e a fase 7 (demo estatico) serve um ficheiro com esta forma.
 * Vale a pena mante-lo estavel.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScanResponse(
        String id,
        String target,
        ScanStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        int hostsUp,
        String baselineScanId,
        boolean cveLookupDegraded,
        List<HostDto> hosts,
        ScanErrorDto error
) {
    public static ScanResponse from(ScanJob job) {
        return from(job, ScanDiff.none());
    }

    /**
     * @param diff comparacao com o baseline atual, calculada na leitura. E por isso
     *             que fixar outro baseline muda logo os flags de todos os scans, sem
     *             reescrever nada -- ao contrario do {@code riskScore}, que fica
     *             gravado com os CVEs que se conheciam na altura
     */
    public static ScanResponse from(ScanJob job, ScanDiff diff) {
        return new ScanResponse(
                job.id().toString(),
                job.target(),
                job.status(),
                job.createdAt(),
                job.startedAt(),
                job.finishedAt(),
                job.durationMs(),
                job.hosts().size(),
                diff.baselineScanId() == null ? null : diff.baselineScanId().toString(),
                job.cveLookupDegraded(),
                job.hosts().stream().map(host -> HostDto.from(host, diff.changeFor(host.ip()))).toList(),
                job.errorCode() == null ? null : new ScanErrorDto(job.errorCode(), job.errorMessage()));
    }

    /** Versao sem a lista de hosts, para o endpoint de listagem nao devolver tudo. */
    public ScanResponse withoutHosts() {
        return new ScanResponse(id, target, status, createdAt, startedAt, finishedAt,
                durationMs, hostsUp, baselineScanId, cveLookupDegraded, List.of(), error);
    }
}
