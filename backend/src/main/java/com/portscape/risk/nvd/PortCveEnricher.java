package com.portscape.risk.nvd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.portscape.config.NvdProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;

/**
 * Anexa a cada porta as falhas conhecidas do que la esta a correr.
 *
 * <p>Ate aqui os CVEs eram consultados, usados para calcular pontos e deitados fora:
 * do {@code CVE-2024-6387 (CVSS 8.1)} sobrava uma frase dentro de uma razao de risco,
 * e os restantes CVEs do mesmo servico sobravam como a contagem "e mais 3". Isto e o
 * que os traz ate ao painel.
 *
 * <p><b>Nao deduplica por host, e isso e deliberado.</b> O
 * {@link com.portscape.risk.rules.VulnerableServiceRule} conta cada CVE uma unica vez
 * por host, porque o nmap cola o CPE do sistema operativo a varios servicos da mesma
 * maquina e uma falha do kernel triplicava-se sozinha no score. Aqui a lista da porta
 * responde a outra pergunta -- <i>o que se sabe estar mal no que corre nesta porta</i>
 * -- e a resposta certa a essa pergunta repete o CVE do kernel no 22, no 80 e no 443.
 * O score continua a cobra-lo uma vez so.
 *
 * <p><b>O tecto.</b> O {@link NvdClient} nao pagina e o NVD devolve ate 2000 CVEs por
 * pagina, portanto um CPE de kernel traria milhares. Passam os de CVSS mais alto, e o
 * total real fica guardado -- truncar sem dizer quanto se truncou seria mentir por
 * omissao.
 */
@Component
public class PortCveEnricher {

    /**
     * Do mais grave para o menos, com os que nao tem CVSS publicado no fim: um CVE sem
     * metricas nao e um CVE benigno, mas tambem nao ha base para o por a frente de um
     * 9.8. Desempate pelo id, para a ordem nao depender da ordem de chegada do NVD.
     */
    private static final Comparator<Cve> WORST_FIRST =
            Comparator.comparing(Cve::cvssScore, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Cve::id);

    private final NvdProperties properties;

    public PortCveEnricher(NvdProperties properties) {
        this.properties = properties;
    }

    /** Os mesmos hosts, com as portas a saber que CVEs tem. */
    public List<Host> attach(List<Host> hosts, CveLookupResult cves) {
        if (cves.byCpe().isEmpty()) {
            return hosts;
        }
        return hosts.stream().map(host -> host.withPorts(attachTo(host.ports(), cves))).toList();
    }

    private List<Port> attachTo(List<Port> ports, CveLookupResult cves) {
        List<Port> enriched = new ArrayList<>(ports.size());
        for (Port port : ports) {
            List<Cve> found = cves.forCpes(port.cpes());
            if (found.isEmpty()) {
                enriched.add(port);
                continue;
            }
            List<Cve> worst = found.stream()
                    .sorted(WORST_FIRST)
                    .limit(properties.maxCvesPerPort())
                    .toList();
            enriched.add(port.withCves(worst, found.size()));
        }
        return List.copyOf(enriched);
    }
}
