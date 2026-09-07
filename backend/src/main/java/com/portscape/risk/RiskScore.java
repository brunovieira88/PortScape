package com.portscape.risk;

import java.util.List;

/**
 * Risco de um host: um valor de 0 a 100, as razoes que o compoem, e o que fazer a
 * seguir.
 *
 * <p>A soma das {@code points} das razoes pode exceder 100 -- o {@code score} esta
 * saturado. E deliberado: o score mapeia diretamente para cor na cena 3D sem
 * normalizacao no frontend, e as razoes continuam a mostrar a gravidade real. E e por
 * isso que cada {@link Remediation} carrega dois numeros em vez de um.
 *
 * <p>O {@code remediation} vive aqui e nao no {@link com.portscape.domain.Host} porque
 * e parte da avaliacao de risco, e porque assim o {@code host.withRisk(...)} que ja
 * existia leva-o consigo sem se acrescentar um wither novo. Vem vazio do
 * {@link RiskScorer} -- e o {@link RemediationPlanner} que o preenche, num segundo
 * passo, porque planear precisa de voltar a pontuar.
 */
public record RiskScore(int score, List<RiskReason> reasons, List<Remediation> remediation) {

    public RiskScore {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        remediation = remediation == null ? List.of() : List.copyOf(remediation);
    }

    /** Score acabado de sair do scorer: ainda sem plano. */
    public RiskScore(int score, List<RiskReason> reasons) {
        this(score, reasons, List.of());
    }

    public static RiskScore none() {
        return new RiskScore(0, List.of(), List.of());
    }

    public RiskScore withRemediation(List<Remediation> plan) {
        return new RiskScore(score, reasons, plan);
    }
}
