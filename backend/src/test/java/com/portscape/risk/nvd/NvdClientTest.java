package com.portscape.risk.nvd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.portscape.config.NvdProperties;

/**
 * O cliente faz dois pedidos: primeiro resolve o nome do produto no dicionario do
 * NVD, depois pede os CVEs desse nome. Os JSON de teste sao respostas reais da API.
 */
class NvdClientTest {

    private static final String BASE_URL = "https://nvd.test";
    private static final String DROPBEAR = "cpe:/a:matt_johnston:dropbear_ssh_server:2017.75";
    private static final String SSH_CPE = "cpe:/a:openbsd:openssh:9.6";

    private MockRestServiceServer server;

    /** Sem espera entre pedidos: o rate limit e testado a parte, nao aqui. */
    private NvdClient clientWith(String apiKey) {
        NvdProperties properties = new NvdProperties(
                true, BASE_URL, apiKey, Duration.ofSeconds(1), Duration.ZERO, Duration.ofDays(7),
                Duration.ofDays(1), null);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new NvdClient(builder, properties, new NvdRateLimiter(properties));
    }

    private static String json(String resource) {
        try (InputStream in = NvdClientTest.class.getResourceAsStream("/" + resource)) {
            assertThat(in).as("recurso de teste %s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** O espaco vai codificado no URL; o matcher compara com o valor tal como sai. */
    private void expectDictionary(String terms, String responseResource) {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/rest/json/cpes/2.0")))
                .andExpect(queryParam("keywordSearch", terms.replace(" ", "%20")))
                .andRespond(withSuccess(json(responseResource), MediaType.APPLICATION_JSON));
    }

    private void expectCveQuery(String cpeName, String responseResource) {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/rest/json/cves/2.0")))
                .andExpect(queryParam("cpeName", cpeName))
                .andRespond(withSuccess(json(responseResource), MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("le uma resposta real do NVD: id, CVSS, vector, data e descricao inglesa")
    void parsesARealNvdResponse() {
        NvdClient client = clientWith(null);
        expectDictionary("dropbear 2017.75", "nvd-cpes-dropbear.json");
        expectCveQuery("cpe:2.3:a:dropbear_ssh_project:dropbear_ssh:2017.75:*:*:*:*:*:*:*",
                "nvd-openssh.json");

        List<Cve> cves = client.findCves(DROPBEAR);

        assertThat(cves).hasSize(3);
        assertThat(cves.get(0).id()).isEqualTo("CVE-2024-6387");
        assertThat(cves.get(0).cvssScore()).isEqualTo(8.1);
        assertThat(cves.get(0).severity()).isEqualTo("HIGH");
        assertThat(cves.get(0).description()).startsWith("A signal handler race condition");
        // O vector e a anatomia da falha, e e o frontend que a traduz -- aqui so tem
        // de chegar inteiro.
        assertThat(cves.get(0).vector())
                .isEqualTo("CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:H");
        // O NVD publica a data sem zona; assume-se UTC.
        assertThat(cves.get(0).published()).isEqualTo(Instant.parse("2024-07-01T13:15:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("resolve o nome do NIST antes de pedir CVEs -- o nome do nmap nao existe la")
    void resolvesTheNistProductNameFirst() {
        NvdClient client = clientWith(null);
        expectDictionary("dropbear 2017.75", "nvd-cpes-dropbear.json");
        expectCveQuery("cpe:2.3:a:dropbear_ssh_project:dropbear_ssh:2017.75:*:*:*:*:*:*:*",
                "nvd-openssh.json");

        client.findCves(DROPBEAR);

        server.verify();
    }

    @Test
    @DisplayName("um CPE marcado como obsoleto pelo NIST nao e escolhido")
    void skipsDeprecatedDictionaryEntries() {
        NvdClient client = clientWith(null);
        expectDictionary("dropbear 2017.75", "nvd-cpes-dropbear.json");
        // Se escolhesse o obsoleto (dropbear_project:dropbear), este matcher falhava.
        expectCveQuery("cpe:2.3:a:dropbear_ssh_project:dropbear_ssh:2017.75:*:*:*:*:*:*:*",
                "nvd-openssh.json");

        client.findCves(DROPBEAR);

        server.verify();
    }

    @Test
    @DisplayName("entre varios candidatos escolhe o de nome igual ao produto do nmap")
    void prefersAnExactProductNameMatch() {
        NvdClient client = clientWith(null);
        expectDictionary("nginx 1.27.5", "nvd-cpes-nginx.json");
        // A resposta lista nginx_open_source primeiro; ganha o "nginx" exato.
        expectCveQuery("cpe:2.3:a:f5:nginx:1.27.5:*:*:*:*:*:*:*", "nvd-openssh.json");

        client.findCves("cpe:/a:igor_sysoev:nginx:1.27.5");

        server.verify();
    }

    @Test
    @DisplayName("no CVSS v2 a severidade vem fora do cvssData, e nao se pode perder por isso")
    void readsTheSeverityOfLegacyCvssV2Entries() {
        NvdClient client = clientWith(null);
        expectDictionary("dropbear 2017.75", "nvd-cpes-dropbear.json");
        expectCveQuery("cpe:2.3:a:dropbear_ssh_project:dropbear_ssh:2017.75:*:*:*:*:*:*:*",
                "nvd-openssh.json");

        Cve legacy = client.findCves(DROPBEAR).get(2);

        // No v3.x/v4.0 o baseSeverity vem dentro do cvssData; no v2 vem ao lado. Olhar
        // so para dentro custava a severidade dos CVEs antigos -- que sao justamente os
        // que aparecem em servicos desactualizados.
        assertThat(legacy.id()).isEqualTo("CVE-2008-3844");
        assertThat(legacy.cvssScore()).isEqualTo(9.3);
        assertThat(legacy.severity()).isEqualTo("HIGH");
        // O vector v2 nao tem prefixo "CVSS:", ao contrario do v3.1/v4.0.
        assertThat(legacy.vector()).isEqualTo("AV:N/AC:M/Au:N/C:C/I:C/A:C");
    }

    @Test
    @DisplayName("um CVE sem metricas publicadas fica sem score nem vector, nao com zero")
    void leavesTheScoreNullWhenNvdPublishesNoMetrics() {
        NvdClient client = clientWith(null);
        expectDictionary("dropbear 2017.75", "nvd-cpes-dropbear.json");
        expectCveQuery("cpe:2.3:a:dropbear_ssh_project:dropbear_ssh:2017.75:*:*:*:*:*:*:*",
                "nvd-openssh.json");

        Cve withoutMetrics = client.findCves(DROPBEAR).get(1);

        assertThat(withoutMetrics.id()).isEqualTo("CVE-2023-51385");
        assertThat(withoutMetrics.cvssScore()).isNull();
        assertThat(withoutMetrics.hasScore()).isFalse();
        // Score, severidade e vector vem todos do mesmo bloco cvssData: ou vem todos,
        // ou nao vem nenhum.
        assertThat(withoutMetrics.severity()).isNull();
        assertThat(withoutMetrics.vector()).isNull();
        // Sem "published" na resposta -- e uma data ausente, nao uma data invalida.
        assertThat(withoutMetrics.published()).isNull();
    }

    @Test
    @DisplayName("se o NVD nao conhecer o produto, nao ha segundo pedido")
    void stopsWhenTheDictionaryHasNoMatch() {
        NvdClient client = clientWith(null);
        expectDictionary("dropbear 2017.75", "nvd-cpes-empty.json");

        assertThat(client.findCves(DROPBEAR)).isEmpty();

        server.verify();
    }

    @Test
    void sendsTheApiKeyHeaderWhenConfigured() {
        NvdClient client = clientWith("segredo");
        server.expect(requestTo(Matchers.any(String.class)))
                .andExpect(header("apiKey", "segredo"))
                .andRespond(withSuccess(json("nvd-cpes-empty.json"), MediaType.APPLICATION_JSON));

        client.findCves(DROPBEAR);

        server.verify();
    }

    @Test
    @DisplayName("um CPE sem versao nunca chega a sair para a rede")
    void neverQueriesForAVersionlessCpe() {
        NvdClient client = clientWith(null);

        assertThat(client.findCves("cpe:/a:busybox:busybox")).isEmpty();

        server.verify(); // nenhuma chamada esperada, nenhuma feita
    }

    @Test
    @DisplayName("429 vira NvdUnavailableException -- resultado incompleto, nao 'sem CVEs'")
    void surfacesRateLimitingAsUnavailable() {
        NvdClient client = clientWith(null);
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.findCves(SSH_CPE))
                .isInstanceOf(NvdUnavailableException.class);
    }

    @Test
    void surfacesServerErrorsAsUnavailable() {
        NvdClient client = clientWith(null);
        server.expect(requestTo(Matchers.any(String.class))).andRespond(withServerError());

        assertThatThrownBy(() -> client.findCves(SSH_CPE))
                .isInstanceOf(NvdUnavailableException.class);
    }

    @Test
    @DisplayName("um corpo que nao e o esperado nao rebenta o cliente")
    void toleratesAnUnexpectedBody() {
        NvdClient client = clientWith(null);
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("{\"message\":\"manutencao\"}", MediaType.APPLICATION_JSON));

        assertThat(client.findCves(SSH_CPE)).isEmpty();
    }
}
