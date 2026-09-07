package com.portscape.risk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.portscape.domain.Host;
import com.portscape.domain.Port;

/**
 * Por onde comecar, e quanto e que cada accao vale.
 *
 * <p><b>Nao ha aqui nenhuma regra de risco duplicada, e e esse o ponto.</b> O
 * {@link RiskScorer#score(RiskInput)} e uma funcao pura -- sem estado, sem I/O, sem base
 * de dados -- por isso simular "e se eu fechasse a 23?" e construir o mesmo
 * {@link RiskInput} sem aquela porta e voltar a chamar o metodo. A resposta passa
 * exatamente pelo codigo que produziu o numero real e nao pode divergir dele.
 *
 * <p>Uma implementacao que subtraisse os pontos da razao dava a resposta errada: o
 * {@link com.portscape.risk.rules.HighRiskPortRule} tem um tecto para as portas sem peso
 * proprio ({@code max-default-port-points}), logo remover uma porta pode nao remover
 * ponto nenhum -- ou remover mais do que a razao dizia, porque tambem leva com ela os
 * pontos de CVE e de porta nova.
 *
 * <p>Custo: duas re-pontuacoes por porta. Um /24 com 45 hosts e 5 portas sao 450
 * chamadas a uma funcao que percorre listas curtas.
 */
@Component
public class RemediationPlanner {

    public static final String CLOSE_PORT = "CLOSE_PORT";
    public static final String UPDATE_SERVICE = "UPDATE_SERVICE";

    private final RiskScorer scorer;

    public RemediationPlanner(RiskScorer scorer) {
        this.scorer = scorer;
    }

    /**
     * As accoes que valem alguma coisa, da mais eficaz para a menos.
     *
     * <p>Um host sem risco devolve lista vazia, e nao uma lista de "nada a fazer": o
     * painel deve nao mostrar a seccao, em vez de mostrar uma seccao a dizer que esta
     * tudo bem.
     */
    public List<Remediation> planFor(RiskInput input) {
        RiskScore current = scorer.score(input);
        if (current.reasons().isEmpty()) {
            return List.of();
        }
        int before = uncappedTotal(current);

        List<Remediation> plan = new ArrayList<>();
        for (Port port : input.host().ports()) {
            evaluate(plan, CLOSE_PORT, without(input.host(), port), input, port, before);
            evaluate(plan, UPDATE_SERVICE, updated(input.host(), port), input, port, before);
        }

        // Empate desfeito pela ordem da porta, para o plano nao depender da ordem em
        // que o nmap calhou devolver os servicos.
        plan.sort(Comparator.comparingInt(Remediation::pointsRemoved).reversed()
                .thenComparing(Remediation::action));
        return List.copyOf(plan);
    }

    /** O mesmo host sem esta porta. */
    private static Host without(Host host, Port port) {
        return host.withPorts(host.ports().stream().filter(other -> other != port).toList());
    }

    /**
     * O mesmo host com esta porta a correr uma versao sem falhas conhecidas.
     *
     * <p><b>Simula-se tirando os CPEs, e nao os CVEs.</b> O
     * {@link com.portscape.risk.rules.VulnerableServiceRule} nao le {@code port.cves()}
     * -- le {@code input.cves().forCpes(port.cpes())}. Limpar a lista de CVEs da porta
     * nao mexia no score nenhum e a accao aparecia a valer zero.
     *
     * <p>Um efeito lateral que e o comportamento certo: se um CVE do kernel estiver
     * colado a esta porta e tambem a outra, tira-lo daqui fa-lo ser cobrado na outra, e
     * a accao vale menos. E verdade -- actualizar um servico nao resolve uma falha do
     * sistema operativo que outro servico tambem expoe.
     *
     * @return {@code null} quando nao ha versao identificada, e portanto nada que se
     *         possa mandar actualizar
     */
    private static Host updated(Host host, Port port) {
        if (port.product() == null || port.cpes().isEmpty()) {
            return null;
        }
        Port patched = new Port(port.number(), port.protocol(), port.state(),
                port.service(), port.product(), port.version(), List.of());
        return host.withPorts(host.ports().stream()
                .map(other -> other == port ? patched : other)
                .toList());
    }

    /**
     * Pontua o host simulado e guarda a diferenca, se houver.
     *
     * <p>Uma accao que nao muda o score nao entra: a lista existe para dizer por onde
     * comecar, e uma entrada que vale zero e ruido que empurra para baixo as que valem.
     */
    private void evaluate(List<Remediation> plan, String code, Host simulated,
                          RiskInput input, Port port, int before) {
        if (simulated == null) {
            return;
        }
        RiskScore after = scorer.score(new RiskInput(
                simulated, input.cves(), input.baselineHost(), input.baselineAvailable()));
        int removed = before - uncappedTotal(after);
        if (removed <= 0) {
            return;
        }
        plan.add(new Remediation(code, describe(code, port), removed, after.score()));
    }

    /**
     * A soma real das razoes, sem o tecto de 100.
     *
     * <p>E dela que sai o {@code pointsRemoved}: e o unico numero que mede o efeito da
     * accao num host saturado, onde o score visivel nao se mexe.
     */
    private static int uncappedTotal(RiskScore score) {
        return score.reasons().stream().mapToInt(RiskReason::points).sum();
    }

    private static String describe(String code, Port port) {
        String where = port.number() + "/" + (port.protocol() == null ? "tcp" : port.protocol());
        if (CLOSE_PORT.equals(code)) {
            return port.service() == null
                    ? "Close port " + where
                    : "Close port " + where + " (" + port.service() + ")";
        }
        return "Update " + port.product()
                + (port.version() == null ? "" : " " + port.version())
                + " on port " + where;
    }
}
