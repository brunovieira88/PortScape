package com.portscape.baseline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.ScanJob;

/**
 * Compara dois scans host a host.
 *
 * <p>Puro e sem estado de proposito: o diff e calculado na leitura e nunca gravado.
 * A comparacao entre dois scans ja persistidos e barata, e gravar as flags deixaria-as
 * a mentir assim que alguem fixasse outro baseline. O oposto do score de risco, que e
 * gravado porque depende dos CVEs conhecidos no momento do scan.
 */
public final class ScanDiffer {

    private ScanDiffer() {
    }

    public static ScanDiff diff(ScanJob current, BaselineSnapshot baseline) {
        if (baseline == null) {
            return ScanDiff.none();
        }

        // O emparelhamento e por identidade (MAC quando existe); as chaves do
        // resultado continuam a ser IPs, que e o que a cena e a API usam para
        // encontrar cada host.
        Map<String, Host> baselineByIdentity = byIdentity(baseline.hosts());
        Map<String, HostChange> changes = new HashMap<>();
        for (Host host : current.hosts()) {
            Host previous = baselineByIdentity.get(host.identity());
            changes.put(host.ip(), previous == null
                    ? HostChange.NEW
                    : (hasChanged(host, previous) ? HostChange.CHANGED : HostChange.UNCHANGED));
        }

        Set<String> present = current.hosts().stream()
                .map(Host::identity).collect(Collectors.toSet());
        List<Host> disappeared = baseline.hosts().stream()
                .filter(host -> !present.contains(host.identity()))
                .toList();

        return new ScanDiff(baseline.scanId(), changes, disappeared);
    }

    /**
     * Mudou se as portas abertas ou o palpite de OS forem diferentes.
     *
     * <p>Nao se compara a versao do servico: e informacao que o nmap acerta de forma
     * intermitente, e trata-la como mudanca encheria a cidade de falsos alarmes.
     */
    private static boolean hasChanged(Host current, Host baseline) {
        return !openPorts(current).equals(openPorts(baseline))
                || !Objects.equals(current.osGuess(), baseline.osGuess());
    }

    private static Set<Integer> openPorts(Host host) {
        return host.ports().stream().map(Port::number).collect(Collectors.toSet());
    }

    /**
     * Indexa por {@link Host#identity()} -- MAC quando o nmap o resolveu, IP quando nao.
     *
     * <p>Era por IP, e num rede com DHCP isso fazia de cada renovacao de aluguer um
     * host desaparecido mais um host novo. O alerta de "maquina nao autorizada", que e
     * a razao de ser deste projecto, disparava por causa do telemovel do proprio dono.
     */
    private static Map<String, Host> byIdentity(List<Host> hosts) {
        return hosts.stream()
                .collect(Collectors.toMap(Host::identity, Function.identity(), (a, b) -> a));
    }
}
