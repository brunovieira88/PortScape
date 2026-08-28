package com.portscape.risk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.portscape.domain.Host;
import com.portscape.risk.nvd.CveLookupResult;

/**
 * Aplica todas as regras a cada host e devolve o score.
 *
 * <p>O score satura em 100 mas as razoes ficam todas: um host com 180 pontos brutos
 * mostra 100 na cidade, e o painel de detalhes explica porque e que nao ha cor mais
 * vermelha que aquela.
 */
@Service
public class RiskScorer {

    public static final int MAX_SCORE = 100;

    private final List<RiskRule> rules;

    public RiskScorer(List<RiskRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * @param hosts    hosts do scan atual
     * @param cves     CVEs ja consultados para os CPEs deste scan
     * @param baseline hosts do scan de referencia, ou null se nao houver baseline
     * @return o score de cada host, por IP
     */
    public Map<String, RiskScore> score(List<Host> hosts, CveLookupResult cves, List<Host> baseline) {
        boolean baselineAvailable = baseline != null;
        Map<String, Host> baselineByIp = baselineAvailable
                ? baseline.stream().collect(Collectors.toMap(Host::ip, Function.identity(), (a, b) -> a))
                : Map.of();

        return hosts.stream().collect(Collectors.toMap(
                Host::ip,
                host -> score(new RiskInput(host, cves, baselineByIp.get(host.ip()), baselineAvailable)),
                (a, b) -> a));
    }

    public RiskScore score(RiskInput input) {
        List<RiskReason> reasons = new ArrayList<>();
        for (RiskRule rule : rules) {
            reasons.addAll(rule.evaluate(input));
        }
        reasons.sort(Comparator.comparingInt(RiskReason::points).reversed());

        int total = reasons.stream().mapToInt(RiskReason::points).sum();
        return new RiskScore(Math.clamp(total, 0, MAX_SCORE), reasons);
    }
}
