package com.portscape.risk;

import static com.portscape.risk.RiskFixtures.PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskBandTest {

    private static RiskBand bandOf(Integer score) {
        return RiskBand.of(score, PROPERTIES);
    }

    @Test
    @DisplayName("as fronteiras sao inclusivas no limiar: 75 e CRITICAL, 74 e HIGH")
    void classifiesExactlyAtTheThresholds() {
        assertThat(bandOf(75)).isEqualTo(RiskBand.CRITICAL);
        assertThat(bandOf(74)).isEqualTo(RiskBand.HIGH);
        assertThat(bandOf(50)).isEqualTo(RiskBand.HIGH);
        assertThat(bandOf(49)).isEqualTo(RiskBand.MEDIUM);
        assertThat(bandOf(25)).isEqualTo(RiskBand.MEDIUM);
        assertThat(bandOf(24)).isEqualTo(RiskBand.LOW);
    }

    @Test
    void classifiesTheExtremes() {
        assertThat(bandOf(100)).isEqualTo(RiskBand.CRITICAL);
        assertThat(bandOf(0)).isEqualTo(RiskBand.LOW);
    }

    @Test
    @DisplayName("um host sem score e UNKNOWN, nunca LOW -- 'nao avaliado' nao e 'seguro'")
    void doesNotPretendAnUnscoredHostIsSafe() {
        assertThat(bandOf(null)).isEqualTo(RiskBand.UNKNOWN);
        assertThat(RiskBand.of((RiskScore) null, PROPERTIES)).isEqualTo(RiskBand.UNKNOWN);
    }

    @Test
    void readsTheBandFromARiskScore() {
        assertThat(RiskBand.of(new RiskScore(90, java.util.List.of()), PROPERTIES))
                .isEqualTo(RiskBand.CRITICAL);
    }

    @Test
    @DisplayName("a ordem das constantes e a ordem dos bairros na cidade")
    void ordersBandsFromWorstToBest() {
        assertThat(RiskBand.values()).containsExactly(
                RiskBand.CRITICAL, RiskBand.HIGH, RiskBand.MEDIUM, RiskBand.LOW, RiskBand.UNKNOWN);
    }
}
