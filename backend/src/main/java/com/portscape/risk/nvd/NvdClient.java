package com.portscape.risk.nvd;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.portscape.config.NvdProperties;

/**
 * Consulta a API 2.0 do NVD por CVEs de um servico detetado pelo nmap.
 *
 * <p><b>Porque sao dois pedidos e nao um.</b> Os CPEs que o nmap emite quase nunca
 * batem certo com o dicionario do NIST: o nmap diz
 * {@code cpe:/a:matt_johnston:dropbear_ssh_server:2017.75} e o NVD conhece
 * {@code cpe:2.3:a:dropbear_ssh_project:dropbear_ssh:2017.75}; para o nginx o nmap diz
 * {@code igor_sysoev} e o NIST diz {@code f5}. Perguntar diretamente pelo nome do nmap
 * devolve zero CVEs -- e zero CVEs lido como "seguro" e exatamente o erro que esta
 * ferramenta nao pode cometer. Por isso primeiro resolve-se o nome no dicionario
 * ({@code /cpes/2.0}) e so depois se pedem os CVEs ({@code /cves/2.0}).
 *
 * <p>Os timeouts nao estao aqui: vivem no
 * {@link com.portscape.config.NvdHttpConfig}, que os aplica ao {@code RestClient}
 * a partir de {@code portscape.nvd.timeout}.
 *
 * <p><b>Nunca lanca por si.</b> Qualquer falha -- rede em baixo, 429, 5xx, JSON
 * inesperado -- vira {@link NvdUnavailableException}, que o {@link CveLookupService}
 * transforma em "resultado incompleto". O NVD e um enriquecimento do scan, nao uma
 * dependencia dele.
 */
@Component
public class NvdClient {

    private static final Logger log = LoggerFactory.getLogger(NvdClient.class);
    private static final String CVE_PATH = "/rest/json/cves/2.0";
    private static final String CPE_PATH = "/rest/json/cpes/2.0";
    private static final int CANDIDATE_LIMIT = 20;
    /** Chaves de metricas do NVD, da mais recente para a mais antiga. */
    private static final String[] METRIC_KEYS =
            {"cvssMetricV40", "cvssMetricV31", "cvssMetricV30", "cvssMetricV2"};

    private final RestClient restClient;
    private final NvdRateLimiter rateLimiter;

    public NvdClient(RestClient.Builder builder, NvdProperties properties, NvdRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        RestClient.Builder configured = builder.baseUrl(properties.baseUrl());
        if (properties.hasApiKey()) {
            configured = configured.defaultHeader("apiKey", properties.apiKey());
        }
        this.restClient = configured.build();
    }

    /**
     * @param cpe22 CPE tal como o nmap o emite, ex. {@code cpe:/a:openbsd:openssh:9.6}
     * @return os CVEs conhecidos, ou lista vazia se o CPE nao for utilizavel ou o NVD
     *         nao reconhecer o produto
     * @throws NvdUnavailableException se a API nao responder como devia
     */
    public List<Cve> findCves(String cpe22) {
        Optional<String> terms = CpeName.toSearchTerms(cpe22);
        if (terms.isEmpty()) {
            log.debug("CPE ignorado (sem versao ou malformado): {}", cpe22);
            return List.of();
        }

        Optional<String> cpeName = resolveCpeName(cpe22, terms.get());
        if (cpeName.isEmpty()) {
            log.debug("O NVD nao conhece nenhum produto correspondente a {}", cpe22);
            return List.of();
        }
        return cvesFor(cpeName.get());
    }

    /**
     * Procura no dicionario do NVD o nome canonico do produto que o nmap detetou.
     *
     * @return o {@code cpeName} do NIST, ou vazio se nada corresponder
     */
    private Optional<String> resolveCpeName(String cpe22, String searchTerms) {
        JsonNode body = get(CPE_PATH, "keywordSearch", searchTerms);
        if (body == null || !body.hasNonNull("products")) {
            return Optional.empty();
        }

        String product = CpeName.productOf(cpe22).orElseThrow();
        String version = CpeName.versionOf(cpe22).orElseThrow();
        String prefix = CpeName.firstToken(product);

        List<String> candidates = new ArrayList<>();
        int seen = 0;
        for (JsonNode entry : body.get("products")) {
            if (++seen > CANDIDATE_LIMIT) {
                break;
            }
            JsonNode cpe = entry.path("cpe");
            // Um CPE marcado como obsoleto pelo NIST tem os CVEs no seu substituto.
            if (cpe.path("deprecated").asBoolean(false)) {
                continue;
            }
            String name = cpe.path("cpeName").asText(null);
            if (name != null && matchesVersion(name, version)) {
                candidates.add(name);
            }
        }

        return candidates.stream()
                // Nome do produto igual ao que o nmap diz e a correspondencia mais forte.
                .filter(name -> product.equals(productOf(name)))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(name -> productOf(name).startsWith(prefix))
                        .findFirst())
                .or(() -> candidates.stream().findFirst());
    }

    private List<Cve> cvesFor(String cpeName) {
        return parse(get(CVE_PATH, "cpeName", cpeName));
    }

    private JsonNode get(String path, String parameter, String value) {
        rateLimiter.acquire();
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParam(parameter, value).build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException e) {
            // O CveLookupService e que decide o que fazer com isto (e faz o log).
            throw new NvdUnavailableException(e);
        }
    }

    /** Campo 5 de um CPE 2.3 ({@code cpe:2.3:part:vendor:product:version:...}). */
    private static String productOf(String cpe23) {
        String[] parts = cpe23.split(":", -1);
        return parts.length > 4 ? parts[4] : "";
    }

    private static boolean matchesVersion(String cpe23, String version) {
        String[] parts = cpe23.split(":", -1);
        return parts.length > 5 && version.equals(parts[5]);
    }

    private static List<Cve> parse(JsonNode body) {
        if (body == null || !body.hasNonNull("vulnerabilities")) {
            return List.of();
        }
        List<Cve> cves = new ArrayList<>();
        for (JsonNode entry : body.get("vulnerabilities")) {
            JsonNode cve = entry.path("cve");
            String id = cve.path("id").asText(null);
            if (id == null) {
                continue;
            }
            JsonNode cvss = bestCvssData(cve.path("metrics"));
            cves.add(new Cve(
                    id,
                    cvss.hasNonNull("baseScore") ? cvss.get("baseScore").asDouble() : null,
                    cvss.path("baseSeverity").asText(null),
                    cvss.path("vectorString").asText(null),
                    publishedAt(cve.path("published")),
                    englishDescription(cve.path("descriptions"))));
        }
        return List.copyOf(cves);
    }

    /** O NVD publica varias versoes do CVSS por CVE; fica a mais recente disponivel. */
    private static JsonNode bestCvssData(JsonNode metrics) {
        for (String key : METRIC_KEYS) {
            JsonNode list = metrics.path(key);
            if (list.isArray() && !list.isEmpty()) {
                return list.get(0).path("cvssData");
            }
        }
        return MissingNode.getInstance();
    }

    /**
     * O NVD publica a data sem zona ({@code 2024-07-01T13:15:00.000}) e em UTC. Uma
     * data ilegivel nao vale um CVE perdido: fica a null e o resto do registo passa.
     */
    private static Instant publishedAt(JsonNode published) {
        String text = published.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            log.debug("Data de publicacao ilegivel, ignorada: {}", text);
            return null;
        }
    }

    private static String englishDescription(JsonNode descriptions) {
        if (!descriptions.isArray()) {
            return null;
        }
        for (JsonNode description : descriptions) {
            if ("en".equals(description.path("lang").asText())) {
                return description.path("value").asText(null);
            }
        }
        return null;
    }
}
