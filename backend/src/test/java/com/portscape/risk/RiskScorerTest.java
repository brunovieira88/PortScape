package com.portscape.risk;

import static com.portscape.risk.RiskFixtures.PROPERTIES;
import static com.portscape.risk.RiskFixtures.cve;
import static com.portscape.risk.RiskFixtures.cves;
import static com.portscape.risk.RiskFixtures.host;
import static com.portscape.risk.RiskFixtures.input;
import static com.portscape.risk.RiskFixtures.port;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.domain.Host;
import com.portscape.risk.nvd.CveLookupResult;
import com.portscape.risk.rules.HighRiskPortRule;
import com.portscape.risk.rules.NewPortsRule;
import com.portscape.risk.rules.UnknownHostRule;
import com.portscape.risk.rules.VulnerableServiceRule;

class RiskScorerTest {

    private static final String SSH_CPE = "cpe:/a:openbsd:openssh:9.6";

    /** As mesmas regras que a aplicacao usa -- e a composicao que interessa testar. */
    private final RiskScorer scorer = new RiskScorer(List.of(
            new HighRiskPortRule(PROPERTIES),
            new VulnerableServiceRule(PROPERTIES),
            new UnknownHostRule(PROPERTIES),
            new NewPortsRule(PROPERTIES)));

    @Test
    void aHostWithNothingOpenScoresZeroWithNoReasons() {
        RiskScore score = scorer.score(input(host("192.168.1.10")));

        assertThat(score.score()).isZero();
        assertThat(score.reasons()).isEmpty();
    }

    @Test
    @DisplayName("um router com Telnet pontua bem acima de uma maquina so com HTTPS")
    void ranksAnInsecureHostAboveABenignOne() {
        int telnet = scorer.score(input(host("192.168.1.1", port(23)))).score();
        int https = scorer.score(input(host("192.168.1.20", port(443)))).score();

        assertThat(telnet).isGreaterThan(https);
        assertThat(https).isLessThan(10);
    }

    @Test
    void sumsThePointsOfEveryRuleThatFired() {
        // 22 (5) + CVE 8.1 (32) = 37
        RiskScore score = scorer.score(new RiskInput(
                host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE)),
                cves(SSH_CPE, cve("CVE-2024-6387", 8.1)),
                null, false));

        assertThat(score.score()).isEqualTo(37);
        assertThat(score.reasons()).extracting(RiskReason::code)
                .containsExactlyInAnyOrder(HighRiskPortRule.CODE, VulnerableServiceRule.CODE);
    }

    @Test
    @DisplayName("o score satura em 100 mas as razoes ficam todas")
    void clampsAtOneHundredWithoutLosingTheEvidence() {
        Host awful = host("192.168.1.66",
                port(23), port(21), port(445), port(3389), port(5900), port(6379));

        RiskScore score = scorer.score(new RiskInput(awful, CveLookupResult.empty(), null, true));

        assertThat(score.score()).isEqualTo(RiskScorer.MAX_SCORE);
        assertThat(score.reasons()).hasSizeGreaterThan(6);
        assertThat(score.reasons().stream().mapToInt(RiskReason::points).sum())
                .isGreaterThan(RiskScorer.MAX_SCORE);
    }

    @Test
    @DisplayName("as razoes vem ordenadas pelo que mais pesa, para o painel mostrar o essencial primeiro")
    void ordersReasonsByWeight() {
        RiskScore score = scorer.score(input(host("192.168.1.1", port(443), port(23), port(80))));

        assertThat(score.reasons()).extracting(RiskReason::points).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("com baseline, um host novo e um host conhecido identicos nao pontuam igual")
    void aNewHostScoresAboveTheSameHostWhenKnown() {
        Host subject = host("192.168.1.50", port(80));
        Map<String, RiskScore> withBaseline = scorer.score(List.of(subject),
                CveLookupResult.empty(), List.of(host("192.168.1.50", port(80))));
        Map<String, RiskScore> newDevice = scorer.score(List.of(subject),
                CveLookupResult.empty(), List.of(host("192.168.1.1")));

        assertThat(newDevice.get("192.168.1.50").score())
                .isGreaterThan(withBaseline.get("192.168.1.50").score());
    }

    @Test
    @DisplayName("sem baseline (primeiro scan) nenhum host e penalizado por ser desconhecido")
    void doesNotPenaliseAnyoneOnTheFirstScan() {
        Map<String, RiskScore> scores = scorer.score(
                List.of(host("192.168.1.50", port(80))), CveLookupResult.empty(), null);

        assertThat(scores.get("192.168.1.50").reasons())
                .extracting(RiskReason::code)
                .doesNotContain(UnknownHostRule.CODE);
    }

    @Test
    void scoresEveryHostInTheScan() {
        Map<String, RiskScore> scores = scorer.score(
                List.of(host("192.168.1.1", port(23)), host("192.168.1.2", port(443))),
                CveLookupResult.empty(), null);

        assertThat(scores).containsOnlyKeys("192.168.1.1", "192.168.1.2");
    }
}
