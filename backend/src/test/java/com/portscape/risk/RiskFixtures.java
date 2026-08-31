package com.portscape.risk;

import java.util.List;
import java.util.Map;

import com.portscape.config.RiskProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.risk.nvd.Cve;
import com.portscape.risk.nvd.CveLookupResult;

/** Fixtures partilhadas pelos testes de scoring, para os testes lerem como o que testam. */
public final class RiskFixtures {

    /** Os mesmos pesos do application.yml, reduzidos ao que os testes precisam. */
    public static final RiskProperties PROPERTIES = new RiskProperties(
            // portWeights
            Map.of(23, 35, 21, 25, 445, 30, 3389, 30, 5900, 25, 6379, 25, 22, 5, 80, 5, 443, 2),
            8, 24,          // defaultPortWeight, maxDefaultPortPoints
            4.0, 3, 15, 7.0, // cvssMultiplier, extraSevereCvePoints, maxExtra, severeThreshold
            25, 8, 24,      // unknownHostPoints, newPortPoints, maxNewPortPoints
            75, 50, 25);    // critical, high, medium

    private RiskFixtures() {
    }

    public static Port port(int number) {
        return new Port(number, "tcp", "open", null, null, null);
    }

    public static Port port(int number, String service, String product, String version, String... cpes) {
        return new Port(number, "tcp", "open", service, product, version, List.of(cpes));
    }

    public static Host host(String ip, Port... ports) {
        return new Host(ip, null, null, null, List.of(ports));
    }

    public static CveLookupResult cves(String cpe, Cve... found) {
        return new CveLookupResult(Map.of(cpe, List.of(found)), false);
    }

    public static Cve cve(String id, double cvss) {
        return new Cve(id, cvss, cvss >= 9 ? "CRITICAL" : cvss >= 7 ? "HIGH" : "MEDIUM", "descricao");
    }

    public static RiskInput input(Host host) {
        return new RiskInput(host, CveLookupResult.empty(), null, false);
    }

    public static RiskInput input(Host host, CveLookupResult cves) {
        return new RiskInput(host, cves, null, false);
    }

    /** Host comparado com um baseline onde ele existia com estas portas. */
    public static RiskInput againstBaseline(Host host, Host baselineHost) {
        return new RiskInput(host, CveLookupResult.empty(), baselineHost, true);
    }

    /** Host comparado com um baseline onde ele nao existia. */
    public static RiskInput missingFromBaseline(Host host) {
        return new RiskInput(host, CveLookupResult.empty(), null, true);
    }
}
