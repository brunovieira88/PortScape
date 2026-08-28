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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.domain.ScanStatus;
import com.portscape.scan.ScanJob;
import com.portscape.scan.ScanService;
import com.portscape.scan.exception.InvalidTargetException;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

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
}
