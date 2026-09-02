package com.portscape.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

import com.portscape.api.dto.ScanDiffResponse;
import com.portscape.api.dto.ScanResponse;
import com.portscape.api.dto.StartScanRequest;
import com.portscape.baseline.BaselineService;
import com.portscape.baseline.ScanDiff;
import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanService;
import com.portscape.layout.CityLayoutCalculator;
import com.portscape.layout.CityLayout;

/**
 * Scans sao assincronos: o POST devolve 202 com o id e o cliente faz polling no GET.
 * Controller fino -- toda a logica esta no {@link ScanService}.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanService scanService;
    private final BaselineService baselineService;
    private final CityLayoutCalculator layoutCalculator;

    public ScanController(ScanService scanService, BaselineService baselineService, CityLayoutCalculator layoutCalculator) {
        this.scanService = scanService;
        this.baselineService = baselineService;
        this.layoutCalculator = layoutCalculator;
    }

    @PostMapping
    public ResponseEntity<ScanResponse> startScan(@RequestBody(required = false) StartScanRequest request) {
        ScanJob job = scanService.startScan(request == null ? null : request.target());
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/scans/" + job.id()))
                .body(ScanResponse.from(job));
    }

    @GetMapping("/{id}")
    public ScanResponse getScan(@PathVariable UUID id) {
        return scanService.findScan(id)
                .map(job -> {
                    ScanDiff diff = baselineService.diffFor(job);
                    CityLayout layout = layoutCalculator.calculate(job.target(), job.hosts(), diff.disappeared());
                    return ScanResponse.from(job, diff, layout);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan nao encontrado: " + id));
    }

    /**
     * Comparacao completa com o baseline, incluindo os hosts que desapareceram -- que
     * nao cabem na resposta do scan porque nao existem nele.
     */
    @GetMapping("/{id}/diff")
    public ScanDiffResponse getDiff(@PathVariable UUID id) {
        return scanService.findScan(id)
                .map(job -> {
                    ScanDiff diff = baselineService.diffFor(job);
                    CityLayout layout = layoutCalculator.calculate(job.target(), job.hosts(), diff.disappeared());
                    return ScanDiffResponse.from(job.id().toString(), diff, layout);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan nao encontrado: " + id));
    }

    /** Sumarios, sem a lista de hosts -- um historico de scans nao precisa dela. */
    @GetMapping
    public List<ScanResponse> listScans() {
        return scanService.findAllScans().stream()
                .map(ScanResponse::from)
                .map(ScanResponse::withoutHosts)
                .toList();
    }

    /**
     * Para um scan que ainda esteja a decorrer.
     *
     * <p>Devolve o job ja cancelado em vez de um 204 vazio: o cliente estava a sondar
     * este scan, e assim actualiza o ecra com esta resposta em vez de esperar pela
     * sondagem seguinte.
     */
    @PostMapping("/{id}/cancel")
    public ScanResponse cancelScan(@PathVariable UUID id) {
        return scanService.cancelScan(id)
                .map(ScanResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan nao encontrado: " + id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScan(@PathVariable UUID id) {
        scanService.deleteScan(id);
    }
}
