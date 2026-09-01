package com.portscape.scan;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                .collect(Collectors.toMap(Host::ip, Function.identity(), (a, b) -> a));

        return discovered.stream()
                .map(host -> mergeHost(host, versionByIp.get(host.ip())))
                .toList();
    }

    private static Host mergeHost(Host discovered, Host versioned) {
        if (versioned == null) {
            return discovered;
        }

        Map<Integer, Port> versionedPorts = versioned.ports().stream()
                .collect(Collectors.toMap(Port::number, Function.identity(), (a, b) -> a));

        List<Port> merged = discovered.ports().stream()
                .map(port -> mergePort(port, versionedPorts.get(port.number())))
                .toList();

        // withPorts e nao um construtor: a descoberta e a fase privilegiada, e e a
        // unica que traz MAC e fabricante. Escrever a copia a mao perdia-os.
        return discovered.withPorts(merged);
    }

    /**
     * Campo a campo, e nao bloco inteiro: a segunda fase so ganha onde tem mesmo algo
     * a dizer. Substituir tudo perdia o nome de servico que a descoberta ja tinha
     * (vem da tabela {@code nmap-services}) sempre que a deteccao de versao nao
     * conseguisse identificar o servico -- o oposto do que esta classe promete.
     */
    private static Port mergePort(Port discovered, Port versioned) {
        if (versioned == null) {
            return discovered;
        }
        return new Port(discovered.number(), discovered.protocol(), discovered.state(),
                betterOf(serviceOrNull(versioned), discovered.service()),
                betterOf(versioned.product(), discovered.product()),
                betterOf(versioned.version(), discovered.version()),
                versioned.cpes().isEmpty() ? discovered.cpes() : versioned.cpes());
    }

    /**
     * {@code tcpwrapped} nao e um servico: e o nmap a dizer que a ligacao foi aceite e
     * fechada sem dar resposta. Como nome de servico vale menos que o palpite por
     * numero de porta que a descoberta ja trazia, por isso conta como "nao sei".
     */
    private static String serviceOrNull(Port versioned) {
        return "tcpwrapped".equalsIgnoreCase(versioned.service()) ? null : versioned.service();
    }

    private static String betterOf(String fromVersionDetection, String fromDiscovery) {
        return fromVersionDetection != null ? fromVersionDetection : fromDiscovery;
    }
}
