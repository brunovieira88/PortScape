package com.portscape.risk.kev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.portscape.config.KevProperties;
import com.portscape.risk.nvd.Cve;
import com.portscape.risk.nvd.CveLookupResult;

/**
 * O catalogo e a segunda fonte: o NVD diz o que a falha permitiria, a CISA diz se
 * alguem a esta a usar. O que estes testes protegem e sobretudo a assimetria -- um
 * catalogo indisponivel nunca pode ler-se como "nao esta a ser explorado".
 */
class KevCatalogTest {

    private static final String FEED = "https://cisa.test/kev.json";
    private static final String SSH_CPE = "cpe:/a:openbsd:openssh:9.6";
    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");

    private MockRestServiceServer server;

    private KevCatalog catalogWith(boolean enabled, Clock clock) {
        KevProperties properties = new KevProperties(
                enabled, FEED, Duration.ofSeconds(5), Duration.ofDays(1));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new KevCatalog(builder.build(), properties, clock);
    }

    private KevCatalog catalogWith(boolean enabled) {
        return catalogWith(enabled, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String json(String resource) {
        try (InputStream in = KevCatalogTest.class.getResourceAsStream("/" + resource)) {
            assertThat(in).as("recurso de teste %s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void expectFeed() {
        server.expect(requestTo(FEED))
                .andRespond(withSuccess(json("kev-catalog.json"), MediaType.APPLICATION_JSON));
    }

    private static CveLookupResult resultWith(Cve... cves) {
        return new CveLookupResult(Map.of(SSH_CPE, List.of(cves)), false);
    }

    private static Cve cve(String id) {
        return new Cve(id, 8.1, "HIGH", "CVSS:3.1/AV:N", null, "descricao");
    }

    private static Cve only(CveLookupResult result) {
        return result.byCpe().get(SSH_CPE).get(0);
    }

    @Test
    @DisplayName("um CVE do catalogo fica marcado como explorado no mundo real")
    void marksCvesListedByCisa() {
        KevCatalog catalog = catalogWith(true);
        expectFeed();

        Cve marked = only(catalog.enrich(resultWith(cve("CVE-2024-6387"))));

        assertThat(marked.isKnownExploited()).isTrue();
        assertThat(marked.kev().dateAdded()).isEqualTo(LocalDate.of(2024, 7, 8));
        assertThat(marked.kev().vulnerabilityName())
                .isEqualTo("OpenSSH Signal Handler Race Condition Vulnerability");
        assertThat(marked.kev().requiredAction()).startsWith("Apply mitigations");
        server.verify();
    }

    @Test
    @DisplayName("o uso em ransomware vem do feed como Known/Unknown, nao como booleano")
    void readsTheRansomwareFlagFromItsStringForm() {
        KevCatalog catalog = catalogWith(true);
        expectFeed();

        CveLookupResult enriched = catalog.enrich(
                resultWith(cve("CVE-2017-0144"), cve("CVE-2024-6387")));
        List<Cve> cves = enriched.byCpe().get(SSH_CPE);

        assertThat(cves.get(0).kev().knownRansomwareUse()).isTrue();
        // "Unknown" no feed -- listado, mas nao observado em ransomware.
        assertThat(cves.get(1).isKnownExploited()).isTrue();
        assertThat(cves.get(1).kev().knownRansomwareUse()).isFalse();
    }

    @Test
    @DisplayName("um CVE que nao consta do catalogo fica sem listagem -- e nao e o mesmo que seguro")
    void leavesUnlistedCvesAlone() {
        KevCatalog catalog = catalogWith(true);
        expectFeed();

        Cve untouched = only(catalog.enrich(resultWith(cve("CVE-2023-51385"))));

        assertThat(untouched.kev()).isNull();
        assertThat(untouched.isKnownExploited()).isFalse();
    }

    @Test
    @DisplayName("uma data ilegivel nao custa a entrada inteira")
    void survivesAnUnparseableDate() {
        KevCatalog catalog = catalogWith(true);
        expectFeed();

        Cve log4j = only(catalog.enrich(resultWith(cve("CVE-2021-44228"))));

        assertThat(log4j.isKnownExploited()).isTrue();
        assertThat(log4j.kev().dateAdded()).isNull();
        assertThat(log4j.kev().knownRansomwareUse()).isTrue();
    }

    @Test
    @DisplayName("o feed em baixo nao rebenta o scan, e nao marca nada como seguro")
    void neverThrowsWhenTheFeedIsDown() {
        KevCatalog catalog = catalogWith(true);
        server.expect(requestTo(FEED)).andRespond(withServerError());

        CveLookupResult result = catalog.enrich(resultWith(cve("CVE-2024-6387")));

        assertThat(only(result).kev()).isNull();
        // A flag do NVD passa intacta: sao fontes distintas e nao se misturam.
        assertThat(result.degraded()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("desligado, nao sai nenhum pedido para a rede")
    void doesNotCallTheFeedWhenDisabled() {
        KevCatalog catalog = catalogWith(false);

        CveLookupResult result = catalog.enrich(resultWith(cve("CVE-2024-6387")));

        assertThat(only(result).kev()).isNull();
        // Nenhuma expectativa registada: um pedido aqui faria o verify falhar.
        server.verify();
    }

    @Test
    @DisplayName("o catalogo e lido uma vez, nao a cada scan")
    void reusesTheCatalogWithinTheRefreshInterval() {
        KevCatalog catalog = catalogWith(true);
        expectFeed();

        catalog.enrich(resultWith(cve("CVE-2024-6387")));
        Cve second = only(catalog.enrich(resultWith(cve("CVE-2024-6387"))));

        assertThat(second.isKnownExploited()).isTrue();
        // Uma so expectativa para duas chamadas: o segundo pedido faria isto falhar.
        server.verify();
    }

    @Test
    @DisplayName("depois de uma falha, um catalogo ja lido continua a valer em vez de esvaziar")
    void keepsTheLastGoodCatalogWhenARefreshFails() {
        MutableClock clock = new MutableClock(NOW);
        KevCatalog catalog = catalogWith(true, clock);
        expectFeed();
        server.expect(requestTo(FEED)).andRespond(withServerError());

        catalog.enrich(resultWith(cve("CVE-2024-6387")));
        clock.advance(Duration.ofDays(2));
        Cve afterFailure = only(catalog.enrich(resultWith(cve("CVE-2024-6387"))));

        // Informacao velha e melhor do que silencio que se le como seguranca.
        assertThat(afterFailure.isKnownExploited()).isTrue();
        server.verify();
    }

    /** Relogio que se pode empurrar, para exercitar o intervalo de recarga. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
