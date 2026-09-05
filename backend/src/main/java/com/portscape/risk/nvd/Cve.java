package com.portscape.risk.nvd;

import java.time.Instant;

import com.portscape.risk.kev.KevListing;

/**
 * Uma vulnerabilidade conhecida, reduzida ao que o scoring e o painel de detalhes
 * precisam.
 *
 * <p>{@code cvssScore} pode ser null: nem todo o CVE tem metricas publicadas. Quando
 * o e, {@code severity} e {@code vector} vao pelo mesmo caminho -- vem todos do mesmo
 * bloco {@code cvssData} da resposta do NVD, ou nao vem nenhum.
 *
 * <p>O {@code vector} ({@code AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H}) e a anatomia da
 * falha: diz se e alcancavel pela rede, se precisa de credenciais e o que compromete.
 * Fica guardado por traduzir -- e apresentacao, e quem a faz e o frontend, que tem de
 * saber explica-la tambem no modo demo, onde nao ha backend nenhum.
 *
 * <p>O {@code kev} nao vem do NVD: e acrescentado depois, a partir do catalogo da
 * CISA. Vive aqui em vez de numa estrutura a parte porque a pergunta que responde --
 * "isto esta a ser explorado?" -- so faz sentido colada ao CVE a que se refere.
 */
public record Cve(
        String id,
        Double cvssScore,
        String severity,
        String vector,
        Instant published,
        String description,
        KevListing kev
) {
    /** Sem listagem KEV -- e o estado com que um CVE sai do NVD. */
    public Cve(String id, Double cvssScore, String severity, String vector,
               Instant published, String description) {
        this(id, cvssScore, severity, vector, published, description, null);
    }

    /** CVEs sem score publicado nao devem contar como 0 nem como criticos. */
    public boolean hasScore() {
        return cvssScore != null;
    }

    /** Confirmado pela CISA como explorado no mundo real. */
    public boolean isKnownExploited() {
        return kev != null;
    }

    public Cve withKev(KevListing listing) {
        return new Cve(id, cvssScore, severity, vector, published, description, listing);
    }
}
