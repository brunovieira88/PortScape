package com.portscape.risk;

import static com.portscape.risk.RiskFixtures.PROPERTIES;
import static com.portscape.risk.RiskFixtures.cve;
import static com.portscape.risk.RiskFixtures.cves;
import static com.portscape.risk.RiskFixtures.host;
import static com.portscape.risk.RiskFixtures.input;
import static com.portscape.risk.RiskFixtures.missingFromBaseline;
import static com.portscape.risk.RiskFixtures.port;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.domain.Host;
import com.portscape.risk.nvd.CveLookupResult;
import com.portscape.risk.rules.HighRiskPortRule;
import com.portscape.risk.rules.NewPortsRule;
import com.portscape.risk.rules.UnknownHostRule;
import com.portscape.risk.rules.VulnerableServiceRule;

/**
 * O plano nao tem regras proprias: e o {@link RiskScorer} outra vez, com uma porta a
 * menos. O que estes testes protegem e que continue a ser assim -- e que o numero que o
 * painel mostra continue a dizer a verdade num host saturado, que e onde e mais facil
 * mentir sem dar por isso.
 */
class RemediationPlannerTest {

    private static final String SSH_CPE = "cpe:/a:openbsd:openssh:9.6";

    private final RiskScorer scorer = new RiskScorer(List.of(
            new HighRiskPortRule(PROPERTIES),
            new VulnerableServiceRule(PROPERTIES),
            new UnknownHostRule(PROPERTIES),
            new NewPortsRule(PROPERTIES)));
    private final RemediationPlanner planner = new RemediationPlanner(scorer);

    private static List<String> actionsOf(List<Remediation> plan) {
        return plan.stream().map(Remediation::action).toList();
    }

    @Test
    @DisplayName("a porta que mais pesa vem primeiro")
    void ordersTheMostEffectiveActionFirst() {
        // 23 vale 35, 443 vale 2.
        Host host = host("192.168.1.10", port(23), port(443));

        List<Remediation> plan = planner.planFor(input(host));

        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).action()).contains("23/tcp");
        assertThat(plan.get(0).pointsRemoved()).isEqualTo(35);
        assertThat(plan.get(1).pointsRemoved()).isEqualTo(2);
    }

    @Test
    @DisplayName("num host saturado a accao vale pontos mas o score visivel nao se mexe")
    void reportsRealPointsEvenWhenTheVisibleScoreIsPinnedAt100() {
        // 35 + 30 + 30 + 25 + 25 = 145 em razoes; o score mostra 100.
        Host host = host("192.168.1.10", port(23), port(445), port(3389), port(21), port(5900));

        RiskScore before = scorer.score(input(host));
        Remediation worst = planner.planFor(input(host)).get(0);

        assertThat(before.score()).isEqualTo(100);
        // Sem isto, o painel dizia "Fechar 23/tcp: 100 -> 100", que se le como avaria.
        assertThat(worst.pointsRemoved()).isEqualTo(35);
        assertThat(worst.scoreAfter()).isEqualTo(100);
    }

    @Test
    @DisplayName("uma porta dentro do tecto das nao ponderadas remove menos do que a razao sugere")
    void accountsForTheCapOnUnweightedPorts() {
        // Cinco portas sem peso proprio: 5 * 8 = 40, limitado a 24 pelo
        // max-default-port-points. Fechar uma so leva 4 * 8 = 32, tambem limitado a 24,
        // portanto nao remove nada -- e a accao nao entra no plano.
        Host host = host("192.168.1.10", port(1234), port(2345), port(3456), port(4567), port(5678));

        List<Remediation> plan = planner.planFor(input(host));

        assertThat(scorer.score(input(host)).score()).isEqualTo(24);
        assertThat(plan).as("fechar uma porta dentro do tecto nao muda o score").isEmpty();
    }

    @Test
    @DisplayName("actualizar o servico tira os pontos de CVE e deixa os da porta")
    void separatesTheCveCostFromThePortCost() {
        Host host = host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE));
        RiskInput withCve = input(host, cves(SSH_CPE, cve("CVE-2024-6387", 8.1)));

        List<Remediation> plan = planner.planFor(withCve);

        // 22 vale 5 pontos de porta; o CVE 8.1 vale 32.
        Remediation update = plan.stream()
                .filter(r -> RemediationPlanner.UPDATE_SERVICE.equals(r.code())).findFirst().orElseThrow();
        Remediation close = plan.stream()
                .filter(r -> RemediationPlanner.CLOSE_PORT.equals(r.code())).findFirst().orElseThrow();

        assertThat(update.pointsRemoved()).isEqualTo(32);
        assertThat(update.action()).contains("OpenSSH 9.6", "22/tcp");
        // Fechar a porta resolve as duas coisas de uma vez, por isso vem primeiro.
        assertThat(close.pointsRemoved()).isEqualTo(37);
        assertThat(plan.get(0)).isEqualTo(close);
    }

    @Test
    @DisplayName("sem versao identificada nao ha nada que se mande actualizar")
    void offersNoUpdateForAServiceItCannotName() {
        Host host = host("192.168.1.10", port(23));

        List<Remediation> plan = planner.planFor(input(host));

        assertThat(plan).extracting(Remediation::code).containsExactly(RemediationPlanner.CLOSE_PORT);
    }

    @Test
    @DisplayName("fechar a porta tambem leva com ela os pontos de porta nova")
    void countsTheBaselineCostOfThePortItCloses() {
        Host now = host("192.168.1.10", port(23), port(443));
        Host beforeBaseline = host("192.168.1.10", port(443));

        List<Remediation> plan = planner.planFor(
                new RiskInput(now, CveLookupResult.empty(), beforeBaseline, true));

        // 35 da porta + 8 de ser nova face ao baseline.
        assertThat(plan.get(0).action()).contains("23/tcp");
        assertThat(plan.get(0).pointsRemoved()).isEqualTo(43);
    }

    @Test
    @DisplayName("um host que so pontua por nao estar no baseline nao tem porta que se feche")
    void offersNothingWhenTheRiskIsNotAboutAPort() {
        // Existir sem autorizacao nao se "corrige" fechando uma porta: autoriza-se, e
        // isso e uma accao sobre o scan (afixar o baseline), nao sobre o host.
        List<Remediation> plan = planner.planFor(missingFromBaseline(host("192.168.1.10")));

        assertThat(scorer.score(missingFromBaseline(host("192.168.1.10"))).score()).isEqualTo(25);
        assertThat(plan).isEmpty();
    }

    @Test
    @DisplayName("um host sem risco devolve lista vazia, nao uma lista a dizer que esta tudo bem")
    void returnsNothingForAHostWithNoRisk() {
        assertThat(planner.planFor(input(host("192.168.1.10")))).isEmpty();
    }

    @Test
    @DisplayName("o plano nao inventa: cada accao bate certo com voltar a pontuar sem ela")
    void everyActionMatchesARealRescore() {
        Host host = host("192.168.1.10",
                port(23), port(445), port(22, "ssh", "OpenSSH", "9.6", SSH_CPE));
        RiskInput full = input(host, cves(SSH_CPE, cve("CVE-2024-6387", 8.1)));
        int before = scorer.score(full).reasons().stream().mapToInt(RiskReason::points).sum();

        for (Remediation action : planner.planFor(full)) {
            if (!RemediationPlanner.CLOSE_PORT.equals(action.code())) {
                continue;
            }
            // Refaz a simulacao a mao e confirma que da o mesmo -- se o planner alguma
            // vez passar a estimar em vez de re-pontuar, isto parte.
            int portNumber = Integer.parseInt(action.action().replaceAll("\\D*(\\d+)/.*", "$1"));
            Host closed = host.withPorts(host.ports().stream()
                    .filter(p -> p.number() != portNumber).toList());
            int after = scorer.score(input(closed, full.cves())).reasons().stream()
                    .mapToInt(RiskReason::points).sum();

            assertThat(action.pointsRemoved())
                    .as("porta %d", portNumber).isEqualTo(before - after);
        }

        assertThat(actionsOf(planner.planFor(full))).isNotEmpty();
    }
}
