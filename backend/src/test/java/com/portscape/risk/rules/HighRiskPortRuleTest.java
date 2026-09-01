package com.portscape.risk.rules;

import static com.portscape.risk.RiskFixtures.PROPERTIES;
import static com.portscape.risk.RiskFixtures.host;
import static com.portscape.risk.RiskFixtures.input;
import static com.portscape.risk.RiskFixtures.port;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.risk.RiskReason;

class HighRiskPortRuleTest {

    private final HighRiskPortRule rule = new HighRiskPortRule(PROPERTIES);

    private int pointsFor(int... ports) {
        return rule.evaluate(input(host("192.168.1.10",
                        java.util.Arrays.stream(ports).mapToObj(com.portscape.risk.RiskFixtures::port)
                                .toArray(com.portscape.domain.Port[]::new))))
                .stream().mapToInt(RiskReason::points).sum();
    }

    @Test
    @DisplayName("Telnet pesa muito mais que HTTPS -- e a diferenca que da cor a cidade")
    void weighsInsecureProtocolsFarAboveOrdinaryOnes() {
        assertThat(pointsFor(23)).isGreaterThan(pointsFor(443) * 10);
        assertThat(pointsFor(445)).isGreaterThan(pointsFor(80));
        assertThat(pointsFor(3389)).isGreaterThan(pointsFor(22));
    }

    @Test
    @DisplayName("um host so com web pontua menos que um host so com SMB, mesmo com mais portas")
    void manyBenignPortsScoreLessThanOneDangerousPort() {
        assertThat(pointsFor(80, 443, 8080)).isLessThan(pointsFor(445));
    }

    @Test
    void usesTheDefaultWeightForPortsNotInTheTable() {
        assertThat(pointsFor(9999)).isEqualTo(8);
    }

    @Test
    @DisplayName("as portas fora da tabela tem tecto: quantidade nao substitui gravidade")
    void capsTheContributionOfPortsOutsideTheTable() {
        int[] tenUnknownPorts = {9001, 9002, 9003, 9004, 9005, 9006, 9007, 9008, 9009, 9010};

        // Sem tecto seriam 10 * 8 = 80 pontos, ou seja CRITICAL para um NAS banal.
        assertThat(pointsFor(tenUnknownPorts)).isEqualTo(24);
        assertThat(pointsFor(tenUnknownPorts)).isLessThan(pointsFor(23));
    }

    @Test
    @DisplayName("as portas com peso proprio continuam a acumular sem tecto")
    void doesNotCapPortsThatHaveTheirOwnWeight() {
        assertThat(pointsFor(23, 445, 3389)).isEqualTo(35 + 30 + 30);
    }

    @Test
    @DisplayName("varias portas fora da tabela dao uma razao so, para o score continuar a ser a soma das razoes")
    void groupsPortsOutsideTheTableIntoASingleReason() {
        List<RiskReason> reasons = rule.evaluate(input(host("192.168.1.10",
                port(23), port(9001), port(9002), port(9003))));

        assertThat(reasons).hasSize(2);
        assertThat(reasons).extracting(RiskReason::points).containsExactlyInAnyOrder(35, 24);
        assertThat(reasons).anySatisfy(reason ->
                assertThat(reason.description()).contains("9001", "9002", "9003"));
    }

    @Test
    void producesOneReasonPerOpenPort() {
        assertThat(rule.evaluate(input(host("192.168.1.10", port(23), port(80)))))
                .hasSize(2)
                .allMatch(reason -> HighRiskPortRule.CODE.equals(reason.code()));
    }

    @Test
    @DisplayName("a razao identifica o servico, para o painel de detalhes nao mostrar so um numero")
    void namesTheServiceInTheReason() {
        List<RiskReason> reasons = rule.evaluate(input(host("192.168.1.1",
                com.portscape.risk.RiskFixtures.port(23, "telnet", "BusyBox telnetd", null))));

        assertThat(reasons).singleElement()
                .extracting(RiskReason::description).asString()
                .contains("23", "telnet", "BusyBox telnetd");
    }

    @Test
    void saysNothingAboutAHostWithNoOpenPorts() {
        assertThat(rule.evaluate(input(host("192.168.1.10")))).isEmpty();
    }
}
