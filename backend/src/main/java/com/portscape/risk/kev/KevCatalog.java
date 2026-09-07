package com.portscape.risk.kev;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.portscape.config.KevHttpConfig;
import com.portscape.config.KevProperties;
import com.portscape.risk.nvd.Cve;
import com.portscape.risk.nvd.CveLookupResult;

/**
 * O catalogo KEV da CISA: os CVEs confirmados como explorados no mundo real.
 *
 * <p><b>Porque e que isto vale a pena.</b> O CVSS mede gravidade teorica -- o que a
 * falha permitiria a quem a explorasse. Nao diz nada sobre se alguem a esta a
 * explorar. Uma falha 7.5 usada em campanhas de ransomware esta semana e mais urgente
 * do que uma 9.8 de 2015 para a qual nunca houve exploit publico, e sem esta segunda
 * fonte as duas sao indistinguiveis no painel.
 *
 * <p><b>Nao pontua.</b> A listagem KEV e informacao, nao peso: o modelo de risco
 * continua a ser o das portas e do CVSS. Foi uma decisao deliberada -- acrescentar
 * uma fonte externa ao score torna o score dependente da disponibilidade dessa fonte,
 * e o mesmo scan passava a dar numeros diferentes consoante a CISA estivesse de pe.
 *
 * <p><b>Nunca lanca.</b> Mesma disciplina que o {@link com.portscape.risk.nvd.NvdClient}:
 * uma falha de rede nao pode custar o scan. Mas ha aqui uma assimetria que importa --
 * um catalogo indisponivel significa "nao sei se esta a ser explorado", nunca "nao
 * esta". Por isso, quando a atualizacao falha, <b>guarda-se o catalogo anterior</b> em
 * vez de o esvaziar: informacao velha e melhor do que silencio que se le como
 * seguranca.
 */
@Component
public class KevCatalog {

    private static final Logger log = LoggerFactory.getLogger(KevCatalog.class);

    /**
     * Depois de uma falha nao se espera o intervalo inteiro para tentar de novo, mas
     * tambem nao se tenta a cada scan: com o feed em baixo, cada tentativa custa o
     * timeout completo ao scan que calhar apanha-la.
     */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(10);

    private final RestClient restClient;
    private final KevProperties properties;
    private final Clock clock;

    /** Ultimo catalogo lido com sucesso, e quando se tentou pela ultima vez. */
    private volatile Map<String, KevListing> listings = Map.of();
    private volatile Instant lastAttempt = null;

    public KevCatalog(@Qualifier(KevHttpConfig.KEV_REST_CLIENT) RestClient restClient,
                      KevProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Marca os CVEs que constam do catalogo.
     *
     * @return o mesmo resultado, com {@link Cve#kev()} preenchido onde houver
     *         correspondencia. A flag {@code degraded} do NVD passa intacta -- sao
     *         fontes distintas e nao se misturam
     */
    public CveLookupResult enrich(CveLookupResult result) {
        if (!properties.enabled() || result.byCpe().isEmpty()) {
            return result;
        }
        Map<String, KevListing> catalog = current();
        if (catalog.isEmpty()) {
            return result;
        }

        Map<String, List<Cve>> enriched = new HashMap<>();
        int marked = 0;
        for (Map.Entry<String, List<Cve>> entry : result.byCpe().entrySet()) {
            List<Cve> cves = entry.getValue().stream()
                    .map(cve -> {
                        KevListing listing = catalog.get(cve.id());
                        return listing == null ? cve : cve.withKev(listing);
                    })
                    .toList();
            marked += (int) cves.stream().filter(Cve::isKnownExploited).count();
            enriched.put(entry.getKey(), cves);
        }
        if (marked > 0) {
            log.info("KEV: {} CVE(s) deste scan constam do catalogo de exploracao ativa", marked);
        }
        return new CveLookupResult(enriched, result.degraded());
    }

    /** O catalogo, recarregado se ja estiver velho. */
    private Map<String, KevListing> current() {
        if (!needsRefresh()) {
            return listings;
        }
        synchronized (this) {
            if (!needsRefresh()) {
                return listings;
            }
            lastAttempt = clock.instant();
            try {
                Map<String, KevListing> fetched = parse(restClient.get()
                        .uri(properties.feedUrl())
                        .retrieve()
                        .body(JsonNode.class));
                listings = fetched;
                log.info("KEV: catalogo actualizado, {} vulnerabilidades conhecidas como exploradas",
                        fetched.size());
            } catch (RuntimeException e) {
                // Guarda-se o que ja se tinha: "nao consegui verificar" nao pode
                // passar a "nao esta a ser explorado".
                log.warn("KEV: nao foi possivel actualizar o catalogo da CISA, fica o anterior "
                        + "({} entradas): {}", listings.size(), e.toString());
            }
            return listings;
        }
    }

    private boolean needsRefresh() {
        if (lastAttempt == null) {
            return true;
        }
        // Uma tentativa falhada deixa o catalogo como estava; e por isso que o
        // intervalo curto se aplica a "esta vazio", nao a "falhou".
        Duration interval = listings.isEmpty() ? RETRY_AFTER_FAILURE : properties.refreshInterval();
        return lastAttempt.plus(interval).isBefore(clock.instant());
    }

    private static Map<String, KevListing> parse(JsonNode body) {
        if (body == null || !body.hasNonNull("vulnerabilities")) {
            return Map.of();
        }
        Map<String, KevListing> parsed = new HashMap<>();
        for (JsonNode entry : body.get("vulnerabilities")) {
            String id = entry.path("cveID").asText(null);
            if (id == null || id.isBlank()) {
                continue;
            }
            parsed.put(id, new KevListing(
                    dateOf(entry.path("dateAdded")),
                    // O feed usa a string "Known"/"Unknown", nao um booleano.
                    "Known".equalsIgnoreCase(entry.path("knownRansomwareCampaignUse").asText("")),
                    entry.path("vulnerabilityName").asText(null),
                    entry.path("requiredAction").asText(null)));
        }
        return Map.copyOf(parsed);
    }

    /** Uma data ilegivel nao vale a entrada inteira: fica a null e o resto passa. */
    private static LocalDate dateOf(JsonNode node) {
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
