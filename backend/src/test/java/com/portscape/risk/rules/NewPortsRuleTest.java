package com.portscape.risk.rules;

import static com.portscape.risk.RiskFixtures.PROPERTIES;
import static com.portscape.risk.RiskFixtures.againstBaseline;
import static com.portscape.risk.RiskFixtures.host;
import static com.portscape.risk.RiskFixtures.input;
import static com.portscape.risk.RiskFixtures.missingFromBaseline;
import static com.portscape.risk.RiskFixtures.port;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.risk.RiskReason;

class NewPortsRuleTest {

    private final NewPortsRule rule = new NewPortsRule(PROPERTIES);

    @Test
    void scoresPortsThatWereNotOpenInTheBaseline() {
        assertThat(rule.evaluate(againstBaseline(
                host("192.168.1.10", port(22), port(3389)),
                host("192.168.1.10", port(22)))))
                .singleElement()
                .satisfies(reason -> {
                    assertThat(reason.code()).isEqualTo(NewPortsRule.CODE);
                    assertThat(reason.points()).isEqualTo(8);
                    assertThat(reason.description()).contains("3389");
                });
    }

    @Test
    void saysNothingWhenTheOpenPortsAreTheSame() {
        assertThat(rule.evaluate(againstBaseline(
                host("192.168.1.10", port(22), port(80)),
                host("192.168.1.10", port(80), port(22))))).isEmpty();
    }

    @Test
    @DisplayName("uma porta que fechou desde o baseline nao soma risco")
    void ignoresPortsThatClosed() {
        assertThat(rule.evaluate(againstBaseline(
                host("192.168.1.10", port(22)),
                host("192.168.1.10", port(22), port(23))))).isEmpty();
    }

    @Test
    void capsTheBonusSoOneHostCannotDominate() {
        assertThat(rule.evaluate(againstBaseline(
                host("192.168.1.10", port(1), port(2), port(3), port(4), port(5), port(6)),
                host("192.168.1.10"))))
                .singleElement()
                .extracting(RiskReason::points).isEqualTo(24);
    }

    @Test
    @DisplayName("num host novo nao conta as portas: o UnknownHostRule ja cobre esse caso")
    void staysSilentForAHostThatIsItselfNew() {
        assertThat(rule.evaluate(missingFromBaseline(host("192.168.1.99", port(23))))).isEmpty();
    }

    @Test
    void staysSilentWhenThereIsNoBaseline() {
        assertThat(rule.evaluate(input(host("192.168.1.10", port(3389))))).isEmpty();
    }
}
