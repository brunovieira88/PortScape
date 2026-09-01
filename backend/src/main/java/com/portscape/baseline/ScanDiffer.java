package com.portscape.baseline;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
        Set<Host> matched = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Host host : current.hosts()) {
            Host previous = previousOf(host, baselineByIdentity);
            if (previous != null) {
                matched.add(previous);
            }
            changes.put(host.ip(), previous == null
                    ? HostChange.NEW
                    : (hasChanged(host, previous) ? HostChange.CHANGED : HostChange.UNCHANGED));
        }

        // Desapareceu quem ficou sem par. Nao se pode perguntar apenas "a identidade
        // dela esta presente?", porque o emparelhamento nem sempre e por identidade --
        // ver o previousOf.
        List<Host> disappeared = baseline.hosts().stream()
                .filter(host -> !matched.contains(host))
                .toList();

        return new ScanDiff(baseline.scanId(), changes, disappeared);
    }

    /**
     * O registo anterior desta maquina, ou null se ela nunca foi vista.
     *
     * <p>Primeiro por identidade. Se nao houver, tenta-se pelo endereco -- <b>mas so
     * quando o registo antigo nao tinha MAC nenhum</b>, ou seja, quando a unica coisa
     * que se sabia dele era o endereco. Ai o endereco e tudo o que ha para comparar, e
     * bater certo e a melhor evidencia disponivel de ser a mesma maquina.
     *
     * <p>Sem esta segunda tentativa, o primeiro scan em que os MACs passaram a ser
     * lidos marcava a rede inteira como nova: cada maquina deixava de bater com o seu
     * proprio registo antigo, aparecia como host novo <i>e</i> como ruina no mesmo
     * endereco -- duas construcoes no mesmo lote, porque a posicao sai do IP.
     *
     * <p>O que isto <b>nao</b> faz e afrouxar o caso que interessa a auditoria: se o
     * registo antigo tinha MAC e o de agora tem outro, alguem ocupou aquele endereco e
     * continua a dar host novo mais ruina, que e exatamente o que se quer ver.
     */
    private static Host previousOf(Host host, Map<String, Host> baselineByIdentity) {
        Host byIdentity = baselineByIdentity.get(host.identity());
        if (byIdentity != null) {
            return byIdentity;
        }
        Host byAddress = baselineByIdentity.get(host.ip());
        return byAddress != null && byAddress.mac() == null ? byAddress : null;
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
