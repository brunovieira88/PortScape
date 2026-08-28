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

import com.portscape.api.dto.ScanResponse;
import com.portscape.api.dto.StartScanRequest;
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

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
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
                .map(ScanResponse::from)
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
