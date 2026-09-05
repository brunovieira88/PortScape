package com.portscape.api.dto;

import com.portscape.risk.Remediation;

/**
 * Uma accao concreta e o que ela vale.
 *
 * <p>Vao os dois numeros de proposito: o {@code pointsRemoved} nao esta saturado e e o
 * que mede o efeito real, o {@code scoreAfter} esta e e o que a cidade vai usar. Num
 * host cujas razoes somem mais de 100 eles discordam, e o cliente tem de o dizer -- "-39
 * pontos, e continua CRITICAL" e a mensagem verdadeira.
 */
public record RemediationDto(String code, String action, int pointsRemoved, int scoreAfter) {

    public static RemediationDto from(Remediation remediation) {
        return new RemediationDto(remediation.code(), remediation.action(),
                remediation.pointsRemoved(), remediation.scoreAfter());
    }
}
