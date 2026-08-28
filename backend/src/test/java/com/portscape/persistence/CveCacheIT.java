package com.portscape.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portscape.config.NvdProperties;
import com.portscape.risk.nvd.Cve;
import com.portscape.risk.nvd.CveCache;

@SpringBootTest
class CveCacheIT extends PostgresTestBase {

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");
    private static final String CPE = "cpe:/a:openbsd:openssh:9.6";
    private static final List<Cve> CVES = List.of(
            new Cve("CVE-2024-6387", 8.1, "HIGH", "race condition no sshd"),
            new Cve("CVE-2023-51385", null, null, "command injection"));

    @Autowired
    private CveLookupRepository repository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    /** TTL de 7 dias, com o relogio fixado no instante dado. */
    private CveCache cacheAt(Instant instant) {
        // TTL de 7 dias para resultados com CVEs, 1 dia para os vazios.
        NvdProperties properties = new NvdProperties(
                true, null, null, null, Duration.ZERO, Duration.ofDays(7), Duration.ofDays(1));
        return new CveCache(repository, objectMapper, properties,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void returnsWhatWasStored() {
        cacheAt(NOW).put(CPE, CVES);

        assertThat(cacheAt(NOW).get(CPE)).contains(CVES);
    }

    @Test
    void missesForAnUnknownCpe() {
        assertThat(cacheAt(NOW).get("cpe:/a:nginx:nginx:1.25")).isEmpty();
    }

    @Test
    @DisplayName("passado o TTL a entrada deixa de valer e o NVD volta a ser consultado")
    void expiresAfterTheConfiguredTtl() {
        cacheAt(NOW).put(CPE, CVES);

        assertThat(cacheAt(NOW.plus(Duration.ofDays(6))).get(CPE)).isPresent();
        assertThat(cacheAt(NOW.plus(Duration.ofDays(8))).get(CPE)).isEmpty();
    }

    @Test
    @DisplayName("guardar o mesmo CPE outra vez atualiza a entrada em vez de duplicar")
    void overwritesAnExistingEntry() {
        cacheAt(NOW).put(CPE, CVES);
        cacheAt(NOW.plus(Duration.ofDays(1))).put(CPE, List.of());

        assertThat(repository.count()).isEqualTo(1);
        assertThat(cacheAt(NOW.plus(Duration.ofDays(1))).get(CPE)).contains(List.of());
    }

    @Test
    @DisplayName("um CPE sem CVEs conhecidos tambem e guardado -- senao reconsultava-se sempre")
    void cachesNegativeResults() {
        cacheAt(NOW).put(CPE, List.of());

        assertThat(cacheAt(NOW).get(CPE)).contains(List.of());
    }

    @Test
    @DisplayName("uma entrada vazia expira mais cedo que uma com CVEs")
    void expiresEmptyResultsSooner() {
        cacheAt(NOW).put(CPE, List.of());
        cacheAt(NOW).put("cpe:/a:nginx:nginx:1.27", CVES);

        // Passadas 2 horas ambas valem.
        assertThat(cacheAt(NOW.plus(Duration.ofHours(2))).get(CPE)).isPresent();
        // Passados 2 dias so a que tem conteudo sobrevive: "sem CVEs" pode ter sido
        // um nome que o NVD nao reconheceu, e isso nao merece uma semana de silencio.
        assertThat(cacheAt(NOW.plus(Duration.ofDays(2))).get(CPE)).isEmpty();
        assertThat(cacheAt(NOW.plus(Duration.ofDays(2))).get("cpe:/a:nginx:nginx:1.27")).isPresent();
    }

    @Test
    void treatsAnUnreadableEntryAsAbsent() {
        repository.save(new CveLookupEntity(CPE, "{\"nao\":\"e uma lista\"}", NOW));

        assertThat(cacheAt(NOW).get(CPE)).isEmpty();
    }
}
