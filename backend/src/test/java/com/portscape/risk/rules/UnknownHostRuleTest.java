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

class UnknownHostRuleTest {

    private final UnknownHostRule rule = new UnknownHostRule(PROPERTIES);

    @Test
    @DisplayName("um host que nao estava no baseline soma risco mesmo sem portas abertas")
    void scoresAHostThatIsNotInTheBaseline() {
        assertThat(rule.evaluate(missingFromBaseline(host("192.168.1.99"))))
                .singleElement()
                .extracting(RiskReason::points).isEqualTo(25);
    }

    @Test
    void saysNothingAboutAHostThatWasAlreadyThere() {
        assertThat(rule.evaluate(againstBaseline(
                host("192.168.1.10", port(22)), host("192.168.1.10", port(22))))).isEmpty();
    }

    @Test
    @DisplayName("no primeiro scan de uma rede nada e 'novo' -- senao era tudo vermelho e nada dizia")
    void staysSilentWhenThereIsNoBaselineAtAll() {
        assertThat(rule.evaluate(input(host("192.168.1.99", port(23))))).isEmpty();
    }

    @Test
    void explainsItself() {
        assertThat(rule.evaluate(missingFromBaseline(host("192.168.1.99"))))
                .singleElement()
                .satisfies(reason -> {
                    assertThat(reason.code()).isEqualTo(UnknownHostRule.CODE);
                    assertThat(reason.description()).contains("novo");
                });
    }
}
