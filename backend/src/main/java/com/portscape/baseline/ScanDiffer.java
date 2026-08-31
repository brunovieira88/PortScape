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

    public static ScanDiff diff(ScanJob current, ScanJob baseline) {
        if (baseline == null) {
            return ScanDiff.none();
        }

        Map<String, Host> baselineByIp = byIp(baseline.hosts());
        Map<String, HostChange> changes = new HashMap<>();
        for (Host host : current.hosts()) {
            Host previous = baselineByIp.get(host.ip());
            changes.put(host.ip(), previous == null
                    ? HostChange.NEW
                    : (hasChanged(host, previous) ? HostChange.CHANGED : HostChange.UNCHANGED));
        }

        Set<String> present = current.hosts().stream().map(Host::ip).collect(Collectors.toSet());
        List<Host> disappeared = baseline.hosts().stream()
                .filter(host -> !present.contains(host.ip()))
                .toList();

        return new ScanDiff(baseline.id(), changes, disappeared);
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

    private static Map<String, Host> byIp(List<Host> hosts) {
        return hosts.stream().collect(Collectors.toMap(Host::ip, Function.identity(), (a, b) -> a));
    }
}
