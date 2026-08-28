package com.portscape.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

import com.portscape.api.dto.ScanDiffResponse;
import com.portscape.api.dto.ScanResponse;
import com.portscape.api.dto.StartScanRequest;
import com.portscape.baseline.BaselineService;
import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanService;

/**
 * Scans sao assincronos: o POST devolve 202 com o id e o cliente faz polling no GET.
 * Controller fino -- toda a logica esta no {@link ScanService}.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanService scanService;
    private final BaselineService baselineService;

    public ScanController(ScanService scanService, BaselineService baselineService) {
        this.scanService = scanService;
        this.baselineService = baselineService;
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
                .map(job -> ScanResponse.from(job, baselineService.diffFor(job)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan nao encontrado: " + id));
    }

    /**
     * Comparacao completa com o baseline, incluindo os hosts que desapareceram -- que
     * nao cabem na resposta do scan porque nao existem nele.
     */
    @GetMapping("/{id}/diff")
    public ScanDiffResponse getDiff(@PathVariable UUID id) {
        return baselineService.diffFor(id)
                .map(diff -> ScanDiffResponse.from(id.toString(), diff))
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
}
