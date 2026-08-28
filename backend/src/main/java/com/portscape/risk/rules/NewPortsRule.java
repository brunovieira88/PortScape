package com.portscape.risk.rules;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.portscape.config.RiskProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.risk.RiskInput;
import com.portscape.risk.RiskReason;
import com.portscape.risk.RiskRule;

/**
 * Portas que um host conhecido nao tinha abertas no baseline.
 *
 * <p>Um servidor que sempre teve o 443 aberto e o estado normal; o mesmo servidor com
 * um 3389 novo e uma mudanca que alguem deve explicar. So se aplica a hosts que ja
 * existiam -- num host novo, o {@link UnknownHostRule} ja cobre o caso e contar as
 * portas outra vez seria pontuar a mesma coisa duas vezes.
 */
@Component
public class NewPortsRule implements RiskRule {

    public static final String CODE = "NEW_PORT";

    private final RiskProperties properties;

    public NewPortsRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RiskReason> evaluate(RiskInput input) {
        Host baseline = input.baselineHost();
        if (baseline == null) {
            return List.of();
        }

        Set<Integer> known = baseline.ports().stream().map(Port::number).collect(Collectors.toSet());
        Set<Integer> added = new TreeSet<>(input.host().ports().stream()
                .map(Port::number)
                .filter(number -> !known.contains(number))
                .collect(Collectors.toSet()));
        if (added.isEmpty()) {
            return List.of();
        }

        int points = Math.min(properties.maxNewPortPoints(),
                added.size() * properties.newPortPoints());
        String list = added.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return List.of(new RiskReason(CODE,
                "Porta(s) abertas desde o scan de referencia: " + list, points));
    }
}
