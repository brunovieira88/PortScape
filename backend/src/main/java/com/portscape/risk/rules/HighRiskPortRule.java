package com.portscape.risk.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.portscape.config.RiskProperties;
import com.portscape.domain.Port;
import com.portscape.risk.RiskInput;
import com.portscape.risk.RiskReason;
import com.portscape.risk.RiskRule;

/**
 * Pontua cada porta aberta pelo que ela representa.
 *
 * <p>Nem todas as portas abertas sao igualmente graves: um 443 numa rede domestica e
 * banal, um 23 (Telnet, sem cifra) ou um 445 (SMB) exposto e outra conversa. E esta
 * diferenca -- e nao o numero de portas -- que faz o edificio ficar vermelho.
 *
 * <p>Para isso ser verdade na pratica, as portas dividem-se em duas:
 * <ul>
 *   <li>as que tem <b>peso proprio</b> em {@code portscape.risk.port-weights} somam
 *       sem tecto: cada uma dessas entradas e um juizo deliberado sobre aquela
 *       porta, e tres servicos de administracao remota expostos <i>devem</i>
 *       acumular;</li>
 *   <li>as <b>restantes</b> partilham um unico tecto
 *       ({@code max-default-port-points}) e uma unica razao. Sem esse tecto, dez
 *       portas desconhecidas somavam 80 pontos e punham CRITICAL um NAS que nao tem
 *       nada de errado, enquanto um Telnet exposto ficava por 35 -- exatamente ao
 *       contrario do que esta regra existe para dizer.</li>
 * </ul>
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
        List<Port> unweighted = new ArrayList<>();

        for (Port port : input.host().ports()) {
            if (!properties.hasWeightFor(port.number())) {
                unweighted.add(port);
                continue;
            }
            int points = properties.weightFor(port.number());
            if (points <= 0) {
                // Peso zero explicito: a porta foi considerada e decidiu-se que nao conta.
                continue;
            }
            reasons.add(new RiskReason(CODE, describe(port), points));
        }

        unweightedReason(unweighted).ifPresent(reasons::add);
        return List.copyOf(reasons);
    }

    /**
     * Uma unica razao para todas as portas fora da tabela, com o somatorio limitado a
     * {@code max-default-port-points}. Junta-las numa razao so nao e economia de
     * texto: e o que mantem o score igual a soma das razoes -- se cada porta tivesse
     * a sua razao, o tecto teria de ser aplicado noutro sitio e o painel de detalhes
     * deixava de explicar o numero que mostra.
     */
    private Optional<RiskReason> unweightedReason(List<Port> ports) {
        if (ports.isEmpty() || properties.defaultPortWeight() <= 0) {
            return Optional.empty();
        }
        int points = Math.min(properties.maxDefaultPortPoints(),
                ports.size() * properties.defaultPortWeight());
        if (ports.size() == 1) {
            return Optional.of(new RiskReason(CODE, describe(ports.get(0)), points));
        }
        String list = ports.stream().map(HighRiskPortRule::label).collect(Collectors.joining(", "));
        return Optional.of(new RiskReason(CODE,
                ports.size() + " portas sem peso atribuido abertas: " + list, points));
    }

    private static String describe(Port port) {
        return "Porta " + label(port) + " aberta";
    }

    /** {@code 23/tcp (telnet: BusyBox telnetd 1.30)} -- o que se sabe da porta. */
    private static String label(Port port) {
        StringBuilder text = new StringBuilder()
                .append(port.number())
                .append('/').append(port.protocol() == null ? "tcp" : port.protocol());
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
