package com.portscape.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.portscape.baseline.Baseline;
import com.portscape.baseline.BaselineNotAllowedException;
import com.portscape.baseline.BaselineService;

@WebMvcTest(BaselineController.class)
class BaselineControllerTest {

    private static final String TARGET = "192.168.1.0/24";
    private static final UUID SCAN_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BaselineService baselineService;

    @Test
    void pinsAScanAsBaseline() throws Exception {
        when(baselineService.pin(SCAN_ID)).thenReturn(new Baseline(TARGET, SCAN_ID, NOW));

        mockMvc.perform(post("/api/baselines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scanId\":\"" + SCAN_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value(TARGET))
                .andExpect(jsonPath("$.scanId").value(SCAN_ID.toString()));
    }

    @Test
    @DisplayName("fixar um scan que nao serve devolve 400 com um codigo, nao um 500")
    void rejectsAnIneligibleScanWithABadRequest() throws Exception {
        when(baselineService.pin(any()))
                .thenThrow(new BaselineNotAllowedException("So um scan concluido pode servir de baseline"));

        mockMvc.perform(post("/api/baselines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scanId\":\"" + SCAN_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BASELINE_NOT_ALLOWED"));
    }

    @Test
    void rejectsARequestWithoutAScanId() throws Exception {
        mockMvc.perform(post("/api/baselines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o target vai em query e nao no caminho: contem uma barra")
    void unpinsByQueryParameter() throws Exception {
        when(baselineService.unpin(TARGET)).thenReturn(true);

        mockMvc.perform(delete("/api/baselines").param("target", TARGET))
                .andExpect(status().isNoContent());

        verify(baselineService).unpin(TARGET);
    }

    @Test
    void unpinningSomethingThatWasNotPinnedIs404() throws Exception {
        when(baselineService.unpin(TARGET)).thenReturn(false);

        mockMvc.perform(delete("/api/baselines").param("target", TARGET))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsPinnedBaselines() throws Exception {
        when(baselineService.findAll()).thenReturn(List.of(new Baseline(TARGET, SCAN_ID, NOW)));

        mockMvc.perform(get("/api/baselines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].target").value(TARGET))
                .andExpect(jsonPath("$[0].pinnedAt").exists());
    }
}
