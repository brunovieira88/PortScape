package com.portscape.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.ScanJob;
import com.portscape.baseline.BaselineService;
import com.portscape.baseline.ScanDiff;
import com.portscape.scan.ScanService;
import com.portscape.scan.exception.InvalidTargetException;
import com.portscape.scan.exception.ScanNotCancellableException;
import com.portscape.layout.CityLayoutCalculator;
import com.portscape.layout.CityLayout;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-28T15:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScanService scanService;

    @MockBean
    private BaselineService baselineService;

    @MockBean
    private CityLayoutCalculator layoutCalculator;

    @org.junit.jupiter.api.BeforeEach
    void noBaselineByDefault() {
        // Sem baseline: o diff nao interfere com o que estes testes verificam.
        when(baselineService.diffFor(any(ScanJob.class))).thenReturn(ScanDiff.none());
        when(layoutCalculator.calculate(any(), any(), any())).thenReturn(
            new CityLayout(java.util.Map.of(), java.util.List.of(), 4.0, 100.0, 100.0)
        );
    }

    @Test
    @DisplayName("POST devolve 202 com Location para o cliente fazer polling")
    void startScanReturnsAccepted() throws Exception {
        when(scanService.startScan(any())).thenReturn(ScanJob.pending(ID, "192.168.1.0/24", NOW));

        mockMvc.perform(post("/api/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"192.168.1.0/24\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/scans/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.target").value("192.168.1.0/24"));
    }

    @Test
    void startScanWorksWithoutABody() throws Exception {
        when(scanService.startScan(any())).thenReturn(ScanJob.pending(ID, "192.168.1.0/24", NOW));

        mockMvc.perform(post("/api/scans"))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("cancelar devolve o scan ja parado, para o cliente nao esperar pela sondagem")
    void cancelReturnsTheStoppedScan() throws Exception {
        when(scanService.cancelScan(ID)).thenReturn(Optional.of(
                ScanJob.pending(ID, "192.168.1.0/24", NOW).running(NOW).cancelled(NOW)));

        mockMvc.perform(post("/api/scans/" + ID + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("cancelar um scan que nao existe da 404")
    void cancelUnknownScanIsNotFound() throws Exception {
        when(scanService.cancelScan(ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/scans/" + ID + "/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("cancelar um scan ja terminado da 409, e nao 400")
    void cancelFinishedScanIsAConflict() throws Exception {
        // Quem sonda de 1500 em 1500 ms pode sempre carregar em cancelar no mesmo
        // instante em que o scan acaba. O pedido nao esta errado -- o estado e que ja
        // nao da, e o cliente distingue-o pelo code.
        when(scanService.cancelScan(ID))
                .thenThrow(new ScanNotCancellableException("O scan ja terminou (DONE) e nao ha nada para cancelar."));

        mockMvc.perform(post("/api/scans/" + ID + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("um target publico da 400 com o codigo de erro no corpo")
    void publicTargetIsRejected() throws Exception {
        when(scanService.startScan(any()))
                .thenThrow(new InvalidTargetException("Target recusado: nao pertence a uma rede privada."));

        mockMvc.perform(post("/api/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"8.8.8.8\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET"))
                .andExpect(jsonPath("$.detail").value("Target recusado: nao pertence a uma rede privada."));
    }

    @Test
    void getScanReturnsHostsAndPorts() throws Exception {
        ScanJob done = ScanJob.pending(ID, "192.168.1.0/24", NOW)
                .running(NOW)
                .done(List.of(new Host("192.168.1.10", "nas.lan", "Linux 5.4 - 5.15", 94,
                        List.of(new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6")))), NOW.plusSeconds(12));
        when(scanService.findScan(ID)).thenReturn(Optional.of(done));

        mockMvc.perform(get("/api/scans/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.hostsUp").value(1))
                .andExpect(jsonPath("$.durationMs").value(12000))
                .andExpect(jsonPath("$.hosts[0].ip").value("192.168.1.10"))
                .andExpect(jsonPath("$.hosts[0].osGuess").value("Linux 5.4 - 5.15"))
                .andExpect(jsonPath("$.hosts[0].portCount").value(1))
                .andExpect(jsonPath("$.hosts[0].ports[0].number").value(22))
                .andExpect(jsonPath("$.hosts[0].ports[0].service").value("ssh"));
    }

    @Test
    void getScanExposesTheFailureReason() throws Exception {
        ScanJob failed = ScanJob.pending(ID, "192.168.1.0/24", NOW)
                .running(NOW)
                .failed("NMAP_PRIVILEGE", "precisa de root", NOW.plusSeconds(1));
        when(scanService.findScan(ID)).thenReturn(Optional.of(failed));

        mockMvc.perform(get("/api/scans/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("NMAP_PRIVILEGE"))
                .andExpect(jsonPath("$.error.message").value("precisa de root"));
    }

    @Test
    void unknownScanReturns404() throws Exception {
        when(scanService.findScan(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/scans/{id}", ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a listagem nao arrasta os hosts de cada scan")
    void listScansOmitsHosts() throws Exception {
        ScanJob done = ScanJob.pending(ID, "192.168.1.0/24", NOW)
                .running(NOW)
                .done(List.of(new Host("192.168.1.10", null, null, null, List.of())), NOW);
        when(scanService.findAllScans()).thenReturn(List.of(done));

        mockMvc.perform(get("/api/scans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hostsUp").value(1))
                .andExpect(jsonPath("$[0].hosts").isEmpty());
    }

    @Test
    @DisplayName("o JSON do scan traz os flags de mudanca e o layout da cidade")
    void exposesBaselineFlagsAndLayout() throws Exception {
        ScanJob job = doneJob();
        when(scanService.findScan(ID)).thenReturn(Optional.of(job));
        when(baselineService.diffFor(any(ScanJob.class))).thenReturn(new ScanDiff(
                BASELINE_ID,
                java.util.Map.of("192.168.1.1", com.portscape.baseline.HostChange.NEW),
                List.of()));
        
        CityLayout layout = new CityLayout(
            java.util.Map.of("192.168.1.1", new com.portscape.layout.HostPosition("192.168.1.1", com.portscape.risk.RiskBand.MEDIUM, 12.0, 4.0)),
            List.of(), 4.0, 100.0, 100.0
        );
        when(layoutCalculator.calculate(any(), any(), any())).thenReturn(layout);

        mockMvc.perform(get("/api/scans/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineScanId").value(BASELINE_ID.toString()))
                .andExpect(jsonPath("$.hosts[0].change").value("NEW"))
                .andExpect(jsonPath("$.hosts[0].isNew").value(true))
                .andExpect(jsonPath("$.hosts[0].isChanged").value(false))
                .andExpect(jsonPath("$.hosts[0].riskBand").value("MEDIUM"))
                .andExpect(jsonPath("$.hosts[0].position.x").value(12.0))
                .andExpect(jsonPath("$.hosts[0].position.z").value(4.0))
                .andExpect(jsonPath("$.layout.width").value(100.0))
                .andExpect(jsonPath("$.ruins").isArray());
    }

    @Test
    @DisplayName("sem baseline os hosts vem UNKNOWN, nao UNCHANGED")
    void reportsUnknownWhenThereIsNoBaseline() throws Exception {
        when(scanService.findScan(ID)).thenReturn(Optional.of(doneJob()));

        mockMvc.perform(get("/api/scans/" + ID))
                .andExpect(jsonPath("$.baselineScanId").doesNotExist())
                .andExpect(jsonPath("$.hosts[0].change").value("UNKNOWN"))
                .andExpect(jsonPath("$.hosts[0].isNew").value(false));
    }

    @Test
    @DisplayName("o risco vai no JSON com as razoes que o explicam")
    void exposesTheRiskScoreAndItsReasons() throws Exception {
        when(scanService.findScan(ID)).thenReturn(Optional.of(doneJob()));

        mockMvc.perform(get("/api/scans/" + ID))
                .andExpect(jsonPath("$.hosts[0].riskScore").value(35))
                .andExpect(jsonPath("$.hosts[0].riskReasons[0].code").value("OPEN_PORT"))
                .andExpect(jsonPath("$.hosts[0].riskReasons[0].points").value(35));
    }

    @Test
    void exposesTheDiffIncludingHostsThatDisappeared() throws Exception {
        ScanJob job = doneJob();
        when(scanService.findScan(ID)).thenReturn(Optional.of(job));
        when(baselineService.diffFor(any(ScanJob.class))).thenReturn(new ScanDiff(
                BASELINE_ID,
                java.util.Map.of("192.168.1.1", com.portscape.baseline.HostChange.UNCHANGED),
                List.of(new Host("192.168.1.42", null, null, null, List.of()))));

        mockMvc.perform(get("/api/scans/" + ID + "/diff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineScanId").value(BASELINE_ID.toString()))
                .andExpect(jsonPath("$.changeByIp['192.168.1.1']").value("UNCHANGED"))
                .andExpect(jsonPath("$.disappeared[0].ip").value("192.168.1.42"));
    }

    @Test
    void diffOfAnUnknownScanIs404() throws Exception {
        when(scanService.findScan(ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/scans/" + ID + "/diff")).andExpect(status().isNotFound());
    }

    private static final UUID BASELINE_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    /** Um scan concluido com um host de risco conhecido, para os testes acima lerem bem. */
    private static ScanJob doneJob() {
        Host host = new Host("192.168.1.1", "router.lan", "Linux", 94,
                List.of(new Port(23, "tcp", "open", "telnet", "BusyBox telnetd", null)),
                new com.portscape.risk.RiskScore(35, List.of(
                        new com.portscape.risk.RiskReason("OPEN_PORT", "Porta 23/tcp aberta (telnet)", 35))));
        return ScanJob.pending(ID, "192.168.1.0/24", NOW).running(NOW).done(List.of(host), NOW);
    }
}
