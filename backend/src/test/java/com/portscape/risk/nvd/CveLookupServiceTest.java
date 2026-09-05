package com.portscape.risk.nvd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.portscape.config.NvdProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CveLookupServiceTest {

    private static final String SSH_CPE = "cpe:/a:openbsd:openssh:9.6";
    private static final Cve CVE = new Cve("CVE-2024-6387", 8.1, "HIGH", null, null, "race condition");

    @Mock
    private NvdClient client;
    @Mock
    private CveCache cache;

    @BeforeEach
    void setUp() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
    }

    private CveLookupService serviceWith(boolean enabled) {
        return new CveLookupService(client, cache, new NvdProperties(
                enabled, null, null, null, Duration.ZERO, Duration.ofDays(7), Duration.ofDays(1), null));
    }

    private static Host hostWith(String ip, String... cpes) {
        return new Host(ip, null, null, null,
                List.of(new Port(22, "tcp", "open", "ssh", "OpenSSH", "9.6", List.of(cpes))));
    }

    @Test
    void returnsTheCvesFoundForEachCpe() {
        when(client.findCves(SSH_CPE)).thenReturn(List.of(CVE));

        CveLookupResult result = serviceWith(true).lookup(List.of(hostWith("192.168.1.10", SSH_CPE)));

        assertThat(result.forCpes(List.of(SSH_CPE))).containsExactly(CVE);
        assertThat(result.degraded()).isFalse();
    }

    @Test
    @DisplayName("o mesmo CPE em varios hosts e consultado uma unica vez")
    void deduplicatesCpesAcrossHosts() {
        when(client.findCves(SSH_CPE)).thenReturn(List.of(CVE));

        serviceWith(true).lookup(List.of(
                hostWith("192.168.1.10", SSH_CPE),
                hostWith("192.168.1.11", SSH_CPE),
                hostWith("192.168.1.12", SSH_CPE)));

        verify(client, times(1)).findCves(SSH_CPE);
    }

    @Test
    void prefersTheCacheOverTheNetwork() {
        when(cache.get(SSH_CPE)).thenReturn(Optional.of(List.of(CVE)));

        CveLookupResult result = serviceWith(true).lookup(List.of(hostWith("192.168.1.10", SSH_CPE)));

        assertThat(result.forCpes(List.of(SSH_CPE))).containsExactly(CVE);
        verify(client, never()).findCves(anyString());
    }

    @Test
    void storesFreshResultsInTheCache() {
        when(client.findCves(SSH_CPE)).thenReturn(List.of(CVE));

        serviceWith(true).lookup(List.of(hostWith("192.168.1.10", SSH_CPE)));

        verify(cache).put(SSH_CPE, List.of(CVE));
    }

    @Test
    @DisplayName("uma falha do NVD marca o resultado como incompleto, nao como 'sem CVEs'")
    void marksTheResultDegradedWhenNvdFails() {
        when(client.findCves(SSH_CPE)).thenThrow(new NvdUnavailableException(new RuntimeException("429")));

        CveLookupResult result = serviceWith(true).lookup(List.of(hostWith("192.168.1.10", SSH_CPE)));

        assertThat(result.degraded()).isTrue();
        assertThat(result.forCpes(List.of(SSH_CPE))).isEmpty();
    }

    @Test
    @DisplayName("se um CPE falhar, os outros sao consultados na mesma")
    void keepsGoingAfterAFailure() {
        String nginx = "cpe:/a:nginx:nginx:1.25";
        when(client.findCves(SSH_CPE)).thenThrow(new NvdUnavailableException(new RuntimeException("boom")));
        when(client.findCves(nginx)).thenReturn(List.of(CVE));

        CveLookupResult result = serviceWith(true).lookup(List.of(hostWith("192.168.1.10", SSH_CPE, nginx)));

        assertThat(result.degraded()).isTrue();
        assertThat(result.forCpes(List.of(nginx))).containsExactly(CVE);
    }

    @Test
    void doesNothingWhenDisabled() {
        CveLookupResult result = serviceWith(false).lookup(List.of(hostWith("192.168.1.10", SSH_CPE)));

        assertThat(result.byCpe()).isEmpty();
        assertThat(result.degraded()).isFalse();
        verify(client, never()).findCves(anyString());
    }

    @Test
    void doesNothingWhenThereAreNoCpes() {
        serviceWith(true).lookup(List.of(new Host("192.168.1.10", null, null, null, List.of())));

        verify(client, never()).findCves(anyString());
    }
}
