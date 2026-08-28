package com.portscape.risk;

import com.portscape.config.RiskProperties;

/**
 * Faixa de risco de um host, derivada do {@link RiskScore}.
 *
 * <p>E isto -- e nao o numero -- que a API expoe para a cena 3D decidir a cor. A
 * paleta e uma decisao de apresentacao e vive no frontend; mudar de tons ou fazer um
 * modo escuro nao tem que obrigar a mexer no backend.
 *
 * <p>A ordem das constantes e a ordem dos bairros na cidade, da esquerda para a
 * direita ({@link #ordinal()} e usado pelo layout).
 */
public enum RiskBand {

    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,

    /**
     * Host sem score calculado -- um scan gravado antes da fase 2, ou um que falhou.
     *
     * <p>Existe separado do {@link #LOW} de proposito: "nunca foi avaliado" nao e
     * "esta tudo bem", e junta-los faria a cidade dizer que uma maquina e segura
     * quando ninguem chegou a olhar para ela.
     */
    UNKNOWN;

    public static RiskBand of(Integer score, RiskProperties properties) {
        if (score == null) {
            return UNKNOWN;
        }
        if (score >= properties.criticalThreshold()) {
            return CRITICAL;
        }
        if (score >= properties.highThreshold()) {
            return HIGH;
        }
        if (score >= properties.mediumThreshold()) {
            return MEDIUM;
        }
        return LOW;
    }

    public static RiskBand of(RiskScore score, RiskProperties properties) {
        return of(score == null ? null : score.score(), properties);
    }
}
