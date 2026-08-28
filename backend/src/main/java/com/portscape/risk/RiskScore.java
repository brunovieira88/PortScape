package com.portscape.risk;

import java.util.List;

/**
 * Risco de um host: um valor de 0 a 100 e as razoes que o compoem.
 *
 * <p>A soma das {@code points} das razoes pode exceder 100 -- o {@code score} esta
 * saturado. E deliberado: o score mapeia diretamente para cor na cena 3D sem
 * normalizacao no frontend, e as razoes continuam a mostrar a gravidade real.
 */
public record RiskScore(int score, List<RiskReason> reasons) {

    public RiskScore {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static RiskScore none() {
        return new RiskScore(0, List.of());
    }
}
