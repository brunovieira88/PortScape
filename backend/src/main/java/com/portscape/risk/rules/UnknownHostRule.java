package com.portscape.risk.rules;

import java.util.List;

import org.springframework.stereotype.Component;

import com.portscape.config.RiskProperties;
import com.portscape.risk.RiskInput;
import com.portscape.risk.RiskReason;
import com.portscape.risk.RiskRule;

/**
 * Um dispositivo que nao estava no baseline soma risco so por existir,
 * independentemente das portas que tenha.
 *
 * <p>E das regras mais uteis na pratica: numa rede que se conhece, o que interessa a
 * uma auditoria e o que apareceu desde a ultima vez -- mesmo que nao tenha nada de
 * obviamente errado aberto.
 */
@Component
public class UnknownHostRule implements RiskRule {

    public static final String CODE = "UNKNOWN_HOST";

    private final RiskProperties properties;

    public UnknownHostRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RiskReason> evaluate(RiskInput input) {
        if (!input.isNewSinceBaseline()) {
            return List.of();
        }
        return List.of(new RiskReason(CODE,
                "Dispositivo novo: nao existia no scan de referencia",
                properties.unknownHostPoints()));
    }
}
