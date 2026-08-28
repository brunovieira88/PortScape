package com.portscape.risk;

import com.portscape.domain.Host;
import com.portscape.risk.nvd.CveLookupResult;

/**
 * O que uma regra precisa de saber para pontuar um host.
 *
 * @param host              o host tal como saiu do scan
 * @param cves              CVEs encontrados para os CPEs deste scan
 * @param baselineHost      o mesmo IP no baseline, ou null se nao existir la
 * @param baselineAvailable se ha sequer um baseline com que comparar. Distinto de
 *                          {@code baselineHost == null}: no primeiro scan de uma rede
 *                          nenhum host esta no baseline, e marcar todos como "novos"
 *                          seria ruido puro em vez de sinal
 */
public record RiskInput(
        Host host,
        CveLookupResult cves,
        Host baselineHost,
        boolean baselineAvailable
) {
    public boolean isNewSinceBaseline() {
        return baselineAvailable && baselineHost == null;
    }
}
