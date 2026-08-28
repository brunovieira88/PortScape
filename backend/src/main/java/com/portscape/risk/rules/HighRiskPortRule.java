package com.portscape.risk.rules;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.portscape.config.RiskProperties;
import com.portscape.domain.Port;
import com.portscape.risk.RiskInput;
import com.portscape.risk.RiskReason;
import com.portscape.risk.RiskRule;

/**
 * Pontua cada porta aberta pelo que ela representa.
 *
 * <p>Nem todas as portas abertas sao igualmente mas:  um 443 numa rede domestica e
 * banal, um 23 (Telnet, sem cifra) ou um 445 (SMB) exposto e outra conversa. E esta
 * diferenca -- e nao o numero de portas -- que faz o edificio ficar vermelho.
 */
@Component
public class HighRiskPortRule implements RiskRule {

    public static final String CODE = "OPEN_PORT";

    private final RiskProperties properties;

    public HighRiskPortRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RiskReason> evaluate(RiskInput input) {
        List<RiskReason> reasons = new ArrayList<>();
        for (Port port : input.host().ports()) {
            int points = properties.weightFor(port.number());
            if (points <= 0) {
                continue;
            }
            reasons.add(new RiskReason(CODE, describe(port), points));
        }
        return List.copyOf(reasons);
    }

    private static String describe(Port port) {
        StringBuilder text = new StringBuilder("Porta ").append(port.number())
                .append('/').append(port.protocol() == null ? "tcp" : port.protocol())
                .append(" aberta");
        if (port.service() != null) {
            text.append(" (").append(port.service());
            if (port.product() != null) {
                text.append(": ").append(port.product());
                if (port.version() != null) {
                    text.append(' ').append(port.version());
                }
            }
            text.append(')');
        }
        return text.toString();
    }
}
