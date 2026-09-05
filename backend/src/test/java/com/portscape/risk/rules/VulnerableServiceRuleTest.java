package com.portscape.risk.rules;

import static com.portscape.risk.RiskFixtures.PROPERTIES;
import static com.portscape.risk.RiskFixtures.cve;
import static com.portscape.risk.RiskFixtures.cves;
import static com.portscape.risk.RiskFixtures.host;
import static com.portscape.risk.RiskFixtures.input;
import static com.portscape.risk.RiskFixtures.port;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.domain.Host;
import com.portscape.risk.RiskInput;
import com.portscape.risk.RiskReason;
import com.portscape.risk.nvd.Cve;
import com.portscape.risk.nvd.CveLookupResult;

class VulnerableServiceRuleTest {

    private static final String SSH_CPE = "cpe:/a:openbsd:openssh:9.6";

    private final VulnerableServiceRule rule = new VulnerableServiceRule(PROPERTIES);

    private static Host sshHost() {
        return host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE));
    }

    @Test
    @DisplayName("os pontos saem do CVSS: 8.1 * 4 = 32")
    void derivesPointsFromTheCvssScore() {
        List<RiskReason> reasons = rule.evaluate(
                input(sshHost(), cves(SSH_CPE, cve("CVE-2024-6387", 8.1))));

        assertThat(reasons).singleElement()
                .extracting(RiskReason::points).isEqualTo(32);
    }

    @Test
    @DisplayName("uma falha critica pesa mais que varias menores -- e o pior CVE que manda")
    void theWorstCveDominatesTheCount() {
        int critical = rule.evaluate(input(sshHost(), cves(SSH_CPE, cve("CVE-A", 9.8))))
                .get(0).points();
        int severalMinor = rule.evaluate(input(sshHost(), cves(SSH_CPE,
                        cve("CVE-B", 3.1), cve("CVE-C", 3.5), cve("CVE-D", 2.0))))
                .get(0).points();

        assertThat(critical).isGreaterThan(severalMinor);
    }

    @Test
    @DisplayName("CVEs graves adicionais somam, mas com tecto")
    void extraSevereCvesAddACappedBonus() {
        Cve[] many = new Cve[]{cve("CVE-1", 9.0), cve("CVE-2", 8.0), cve("CVE-3", 8.0),
                cve("CVE-4", 8.0), cve("CVE-5", 8.0), cve("CVE-6", 8.0),
                cve("CVE-7", 8.0), cve("CVE-8", 8.0), cve("CVE-9", 8.0)};

        int points = rule.evaluate(input(sshHost(), cves(SSH_CPE, many))).get(0).points();

        // 9.0 * 4 = 36, mais o bonus limitado a 15.
        assertThat(points).isEqualTo(36 + 15);
    }

    @Test
    @DisplayName("um CVE sem CVSS publicado nao pontua -- nao ha base para lhe dar peso")
    void ignoresCvesWithoutAPublishedScore() {
        CveLookupResult onlyUnscored = new CveLookupResult(
                Map.of(SSH_CPE, List.of(new Cve("CVE-2023-51385", null, null, null, null, "sem metricas"))), false);

        assertThat(rule.evaluate(input(sshHost(), onlyUnscored))).isEmpty();
    }

    @Test
    void saysNothingWhenNoCvesAreKnown() {
        assertThat(rule.evaluate(input(sshHost()))).isEmpty();
    }

    @Test
    @DisplayName("a razao nomeia o CVE e o servico, para o score ser auditavel")
    void namesTheCveAndTheService() {
        RiskReason reason = rule.evaluate(
                input(sshHost(), cves(SSH_CPE, cve("CVE-2024-6387", 8.1)))).get(0);

        assertThat(reason.code()).isEqualTo(VulnerableServiceRule.CODE);
        assertThat(reason.description()).contains("CVE-2024-6387", "8.1", "OpenSSH 9.6", "22");
    }

    @Test
    @DisplayName("uma porta sem CPE nao herda os CVEs de outra porta do mesmo host")
    void doesNotLeakCvesBetweenPorts() {
        Host mixed = host("192.168.1.10",
                port(22, "ssh", "OpenSSH", "9.6", SSH_CPE),
                port(80, "http", "lighttpd", "1.4.59"));

        assertThat(rule.evaluate(input(mixed, cves(SSH_CPE, cve("CVE-2024-6387", 8.1)))))
                .hasSize(1);
    }

    @Test
    @DisplayName("um CVE partilhado por varias portas conta uma vez -- o CPE do OS nao se triplica")
    void countsEachCveOncePerHost() {
        // O nmap anexa o CPE do kernel ao SSH e ao HTTP da mesma maquina.
        String kernel = "cpe:/o:linux:linux_kernel:5.15";
        Host host = host("192.168.1.10",
                port(22, "ssh", "OpenSSH", "9.6", kernel),
                port(80, "http", "nginx", "1.18", kernel));

        RiskInput input = new RiskInput(host,
                cves(kernel, cve("CVE-2024-0001", 9.8)), null, false);

        assertThat(rule.evaluate(input)).singleElement()
                .extracting(RiskReason::points).isEqualTo(39);
    }
}
