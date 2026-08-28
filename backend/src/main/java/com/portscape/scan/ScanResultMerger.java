package com.portscape.scan;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.portscape.domain.Host;
import com.portscape.domain.Port;

/**
 * Junta o resultado da fase de descoberta (portas + OS, privilegiada) com o da
 * fase de deteccao de versao (sem privilegios) -- ver {@link NmapCommandBuilder}
 * para o porque de serem duas fases.
 *
 * <p>A descoberta e a fonte de verdade para que portas existem e o estado delas;
 * a deteccao de versao so acrescenta {@code service}/{@code product}/{@code version}
 * quando encontra a mesma porta. Um host ou porta que a segunda fase nao veja (por
 * ter falhado, ou por timing) fica exatamente como a descoberta o reportou --
 * nunca se perde informacao por a segunda fase ser mais fraca.
 */
final class ScanResultMerger {

    private ScanResultMerger() {
    }

    static List<Host> merge(List<Host> discovered, List<Host> versionInfo) {
        Map<String, Host> versionByIp = versionInfo.stream()
                .collect(java.util.stream.Collectors.toMap(Host::ip, Function.identity(), (a, b) -> a));

        return discovered.stream()
                .map(host -> mergeHost(host, versionByIp.get(host.ip())))
                .toList();
    }

    private static Host mergeHost(Host discovered, Host versioned) {
        if (versioned == null) {
            return discovered;
        }

        Map<Integer, Port> versionedPorts = versioned.ports().stream()
                .collect(java.util.stream.Collectors.toMap(Port::number, Function.identity(), (a, b) -> a));

        List<Port> merged = discovered.ports().stream()
                .map(port -> mergePort(port, versionedPorts.get(port.number())))
                .toList();

        return new Host(discovered.ip(), discovered.hostname(), discovered.osGuess(), discovered.osAccuracy(), merged);
    }

    private static Port mergePort(Port discovered, Port versioned) {
        if (versioned == null) {
            return discovered;
        }
        return new Port(discovered.number(), discovered.protocol(), discovered.state(),
                versioned.service(), versioned.product(), versioned.version(), versioned.cpes());
    }
}
