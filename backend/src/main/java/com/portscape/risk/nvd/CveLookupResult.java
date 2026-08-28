package com.portscape.risk.nvd;

import java.util.List;
import java.util.Map;

/**
 * CVEs encontrados para um conjunto de CPEs.
 *
 * @param byCpe    CVEs por CPE consultado; um CPE sem CVEs conhecidos aparece com
 *                 lista vazia
 * @param degraded pelo menos uma consulta falhou. Distingue "nao ha CVEs" de "nao foi
 *                 possivel verificar" -- sem esta flag, uma API do NIST em baixo fazia
 *                 a rede inteira parecer segura, que e o pior erro que esta ferramenta
 *                 podia cometer
 */
public record CveLookupResult(
        Map<String, List<Cve>> byCpe,
        boolean degraded
) {
    public CveLookupResult {
        byCpe = byCpe == null ? Map.of() : Map.copyOf(byCpe);
    }

    public static CveLookupResult empty() {
        return new CveLookupResult(Map.of(), false);
    }

    /** Todos os CVEs de um conjunto de CPEs, sem repetidos. */
    public List<Cve> forCpes(List<String> cpes) {
        return cpes.stream()
                .flatMap(cpe -> byCpe.getOrDefault(cpe, List.of()).stream())
                .distinct()
                .toList();
    }
}
