package com.portscape.persistence;

import com.portscape.risk.Remediation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Uma accao do plano de remediacao, gravada contra o host a que se aplica.
 *
 * <p>{@code @Embeddable} e nao entidade propria pela mesma razao que o
 * {@link CveEmbeddable}: uma accao nao tem vida fora do host, nao se consulta por si e
 * desaparece com ele.
 */
@Embeddable
public class RemediationEmbeddable {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "action", nullable = false)
    private String action;

    /** Sem tecto -- ver o javadoc de {@link Remediation}. */
    @Column(name = "points_removed", nullable = false)
    private int pointsRemoved;

    @Column(name = "score_after", nullable = false)
    private int scoreAfter;

    protected RemediationEmbeddable() {
        // exigido pelo JPA
    }

    public static RemediationEmbeddable from(Remediation remediation) {
        RemediationEmbeddable entity = new RemediationEmbeddable();
        entity.code = remediation.code();
        entity.action = remediation.action();
        entity.pointsRemoved = remediation.pointsRemoved();
        entity.scoreAfter = remediation.scoreAfter();
        return entity;
    }

    public Remediation toDomain() {
        return new Remediation(code, action, pointsRemoved, scoreAfter);
    }
}
