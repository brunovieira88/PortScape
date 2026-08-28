package com.portscape.risk.nvd;

/**
 * Uma vulnerabilidade conhecida, reduzida ao que o scoring e o painel de detalhes
 * precisam. {@code cvssScore} pode ser null: nem todo o CVE tem metricas publicadas.
 */
public record Cve(
        String id,
        Double cvssScore,
        String severity,
        String description
) {
    /** CVEs sem score publicado nao devem contar como 0 nem como criticos. */
    public boolean hasScore() {
        return cvssScore != null;
    }
}
