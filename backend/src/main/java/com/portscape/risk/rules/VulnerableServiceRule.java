package com.portscape.risk.rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.portscape.config.RiskProperties;
import com.portscape.domain.Port;
import com.portscape.risk.RiskInput;
import com.portscape.risk.RiskReason;
import com.portscape.risk.RiskRule;
import com.portscape.risk.nvd.Cve;

/**
 * Pontua os CVEs conhecidos dos servicos detetados.
 *
 * <p>O peso vem do CVSS do pior CVE, e nao da contagem: dez falhas menores nao sao
 * pior do que uma execucao remota de codigo. Os restantes CVEs graves somam um
 * incremento pequeno e limitado, so para distinguir um servico com um problema de um
 * servico que esta claramente ao abandono.
 *
 * <p>CVEs sem CVSS publicado sao ignorados de proposito: nao ha base para lhes
 * atribuir peso, e inventar um seria pior do que nao contar.
 *
 * <p><b>Cada CVE conta uma vez por host, nao uma vez por porta.</b> O nmap costuma
 * anexar o CPE do sistema operativo ({@code cpe:/o:linux:linux_kernel:5.15}) a
 * varios servicos da mesma maquina; sem esta salvaguarda, uma falha do kernel
 * pontuava no 22, no 80 e no 443 e triplicava-se sozinha. O CVE fica atribuido a
 * primeira porta onde aparece, que e a que o painel de detalhes vai mostrar.
 */
@Component
public class VulnerableServiceRule implements RiskRule {

    public static final String CODE = "KNOWN_CVE";

    private final RiskProperties properties;

    public VulnerableServiceRule(RiskProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<RiskReason> evaluate(RiskInput input) {
        List<RiskReason> reasons = new ArrayList<>();
        Set<String> alreadyCounted = new HashSet<>();

        for (Port port : input.host().ports()) {
            List<Cve> scored = uncountedCvesOf(port, input, alreadyCounted);
            if (scored.isEmpty()) {
                continue;
            }
            reasons.add(reasonFor(port, scored));
        }
        return List.copyOf(reasons);
    }

    /**
     * Os CVEs pontuaveis desta porta que ainda nao foram atribuidos a outra porta do
     * mesmo host, do mais grave para o menos.
     */
    private static List<Cve> uncountedCvesOf(Port port, RiskInput input, Set<String> alreadyCounted) {
        List<Cve> scored = new ArrayList<>();
        for (Cve cve : input.cves().forCpes(port.cpes())) {
            if (cve.hasScore() && alreadyCounted.add(cve.id())) {
                scored.add(cve);
            }
        }
        scored.sort(Comparator.comparingDouble(Cve::cvssScore).reversed());
        return scored;
    }

    private RiskReason reasonFor(Port port, List<Cve> scored) {
        Cve worst = scored.get(0);
        int points = (int) Math.round(worst.cvssScore() * properties.cvssMultiplier());

        long otherSevere = scored.stream().skip(1)
                .filter(cve -> cve.cvssScore() >= properties.severeCvssThreshold())
                .count();
        points += Math.min(properties.maxExtraSevereCvePoints(),
                (int) otherSevere * properties.extraSevereCvePoints());

        StringBuilder text = new StringBuilder(worst.id())
                .append(" (CVSS ").append(worst.cvssScore()).append(')');
        if (port.product() != null) {
            text.append(" em ").append(port.product());
            if (port.version() != null) {
                text.append(' ').append(port.version());
            }
        }
        text.append(" na porta ").append(port.number());
        if (scored.size() > 1) {
            text.append(" -- e mais ").append(scored.size() - 1).append(" CVE(s) conhecido(s)");
        }
        return new RiskReason(CODE, text.toString(), points);
    }
}
