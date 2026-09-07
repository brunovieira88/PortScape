package com.portscape.risk.nvd;

import static com.portscape.risk.RiskFixtures.cve;
import static com.portscape.risk.RiskFixtures.host;
import static com.portscape.risk.RiskFixtures.port;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.config.NvdProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;

/**
 * O enricher e o que traz os CVEs ate ao painel. O que estes testes protegem sao as
 * duas decisoes que ele encerra: que a lista da porta responde a uma pergunta
 * diferente da do score, e que ha um tecto -- e que o tecto se declara.
 */
class PortCveEnricherTest {

    private static final String SSH_CPE = "cpe:/a:openbsd:openssh:9.6";
    private static final String HTTP_CPE = "cpe:/a:lighttpd:lighttpd:1.4.59";
    private static final String KERNEL_CPE = "cpe:/o:linux:linux_kernel:5.15";

    private static PortCveEnricher enricherWith(int maxPerPort) {
        return new PortCveEnricher(
                new NvdProperties(true, null, null, null, null, null, null, maxPerPort));
    }

    private static Port onlyPort(List<Host> hosts) {
        return hosts.get(0).ports().get(0);
    }

    private static List<String> idsOf(Port port) {
        return port.cves().stream().map(Cve::id).toList();
    }

    private static CveLookupResult found(Map<String, List<Cve>> byCpe) {
        return new CveLookupResult(byCpe, false);
    }

    @Test
    @DisplayName("a porta fica a saber que falhas tem o que la corre")
    void attachesTheCvesOfThePortsCpe() {
        Host ssh = host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE));

        List<Host> enriched = enricherWith(25).attach(List.of(ssh),
                found(Map.of(SSH_CPE, List.of(cve("CVE-2024-6387", 8.1)))));

        assertThat(idsOf(onlyPort(enriched))).containsExactly("CVE-2024-6387");
        assertThat(onlyPort(enriched).cveTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("do pior CVSS para o menos grave -- e o pior que o painel mostra primeiro")
    void ordersTheWorstFirst() {
        Host ssh = host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE));

        List<Host> enriched = enricherWith(25).attach(List.of(ssh), found(Map.of(SSH_CPE,
                List.of(cve("CVE-2020-0001", 5.3), cve("CVE-2024-0002", 9.8),
                        cve("CVE-2022-0003", 7.5)))));

        assertThat(idsOf(onlyPort(enriched)))
                .containsExactly("CVE-2024-0002", "CVE-2022-0003", "CVE-2020-0001");
    }

    @Test
    @DisplayName("um CVE sem CVSS publicado vai para o fim, nao para a frente de um 9.8")
    void putsUnscoredCvesLast() {
        Host ssh = host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE));
        Cve unscored = new Cve("CVE-2023-51385", null, null, null, null, "sem metricas");

        List<Host> enriched = enricherWith(25).attach(List.of(ssh),
                found(Map.of(SSH_CPE, List.of(unscored, cve("CVE-2024-0002", 9.8)))));

        // Sem score nao e benigno, mas tambem nao ha base para o por a frente.
        assertThat(idsOf(onlyPort(enriched)))
                .containsExactly("CVE-2024-0002", "CVE-2023-51385");
    }

    @Test
    @DisplayName("acima do tecto guarda os piores, e o total diz quantos eram mesmo")
    void truncatesButDeclaresTheRealTotal() {
        Host ssh = host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE));
        // O caso real: um CPE de kernel devolve milhares e o cliente do NVD nao pagina.
        List<Cve> many = List.of(
                cve("CVE-2020-0001", 4.0), cve("CVE-2020-0002", 9.8),
                cve("CVE-2020-0003", 7.5), cve("CVE-2020-0004", 6.1));

        Port port = onlyPort(enricherWith(2).attach(List.of(ssh), found(Map.of(SSH_CPE, many))));

        assertThat(idsOf(port)).containsExactly("CVE-2020-0002", "CVE-2020-0003");
        // Mostrar 2 sem dizer que eram 4 seria mentir por omissao.
        assertThat(port.cveTotal()).isEqualTo(4);
    }

    @Test
    @DisplayName("uma porta sem CPEs nao ganha CVEs de outra")
    void leavesPortsWithoutCpesAlone() {
        Host host = host("192.168.1.10",
                port(22, "ssh", "OpenSSH", "9.6", SSH_CPE),
                port(23, "telnet", "BusyBox telnetd", null));

        List<Host> enriched = enricherWith(25).attach(List.of(host),
                found(Map.of(SSH_CPE, List.of(cve("CVE-2024-6387", 8.1)))));

        Port telnet = enriched.get(0).ports().get(1);
        assertThat(telnet.cves()).isEmpty();
        assertThat(telnet.cveTotal()).isZero();
    }

    @Test
    @DisplayName("uma falha do kernel aparece em todas as portas que a partilham -- ao contrario do score")
    void repeatsAnOsCveAcrossEveryPortThatCarriesIt() {
        // O nmap cola o CPE do sistema operativo a varios servicos da mesma maquina. O
        // VulnerableServiceRule conta essa falha uma unica vez, senao triplicava-se no
        // score; aqui a lista da porta responde a outra pergunta -- "o que se sabe
        // estar mal no que corre nesta porta" -- e a repeticao e a resposta certa.
        Host host = host("192.168.1.10",
                port(22, "ssh", "OpenSSH", "9.6", SSH_CPE, KERNEL_CPE),
                port(80, "http", "lighttpd", "1.4.59", HTTP_CPE, KERNEL_CPE));
        Cve kernel = cve("CVE-2024-KERNEL", 7.8);

        List<Host> enriched = enricherWith(25).attach(List.of(host), found(Map.of(
                SSH_CPE, List.of(cve("CVE-2024-6387", 8.1)),
                HTTP_CPE, List.of(),
                KERNEL_CPE, List.of(kernel))));

        assertThat(idsOf(enriched.get(0).ports().get(0)))
                .containsExactly("CVE-2024-6387", "CVE-2024-KERNEL");
        assertThat(idsOf(enriched.get(0).ports().get(1)))
                .containsExactly("CVE-2024-KERNEL");
    }

    @Test
    @DisplayName("sem CVEs nenhuns, os hosts passam como estavam")
    void returnsTheHostsUntouchedWhenThereIsNothingToAttach() {
        List<Host> hosts = List.of(host("192.168.1.10", port(22, "ssh", "OpenSSH", "9.6", SSH_CPE)));

        List<Host> enriched = enricherWith(25).attach(hosts, CveLookupResult.empty());

        // A mesma lista, nao uma copia: sem CVEs nao ha nada que reconstruir.
        assertThat(enriched).isSameAs(hosts);
        assertThat(onlyPort(enriched).cves()).isEmpty();
    }

    @Test
    @DisplayName("o resto do host sobrevive -- mac e vendor nao se perdem ao anexar CVEs")
    void preservesEverythingElseOnTheHost() {
        // O Host.withPorts ja perdeu mac/vendor uma vez numa copia manual; o wither
        // existe por causa disso, e este teste e o que impede a regressao por aqui.
        Host host = new Host("192.168.1.10", "68:AA:C4:F8:93:9F", "Altice Labs",
                "router.lan", "Linux 5.4 - 5.15", 94,
                List.of(port(22, "ssh", "OpenSSH", "9.6", SSH_CPE)), null);

        Host enriched = enricherWith(25).attach(List.of(host),
                found(Map.of(SSH_CPE, List.of(cve("CVE-2024-6387", 8.1))))).get(0);

        assertThat(enriched.mac()).isEqualTo("68:AA:C4:F8:93:9F");
        assertThat(enriched.vendor()).isEqualTo("Altice Labs");
        assertThat(enriched.osAccuracy()).isEqualTo(94);
    }
}
