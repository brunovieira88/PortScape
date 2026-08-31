package com.portscape.risk.nvd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.portscape.config.NvdProperties;
import com.portscape.domain.Host;
import com.portscape.domain.Port;

/**
 * Ponto unico de consulta de CVEs para um scan: deduplica CPEs, tenta a cache antes
 * da rede e nunca deixa uma falha do NVD chegar ao {@code ScanService}.
 */
@Service
public class CveLookupService {

    private static final Logger log = LoggerFactory.getLogger(CveLookupService.class);

    /** O {@link NvdClient} faz dois pedidos por CPE: dicionario e depois CVEs. */
    private static final int REQUESTS_PER_CPE = 2;

    private final NvdClient client;
    private final CveCache cache;
    private final NvdProperties properties;

    public CveLookupService(NvdClient client, CveCache cache, NvdProperties properties) {
        this.client = client;
        this.cache = cache;
        this.properties = properties;
    }

    /**
     * Consulta os CVEs de todos os servicos encontrados no scan.
     *
     * <p>Deduplicar os CPEs antes de sair para a rede e o que evita consultar dez
     * vezes o mesmo OpenSSH numa rede onde todas as maquinas correm a mesma versao.
     */
    public CveLookupResult lookup(List<Host> hosts) {
        if (!properties.enabled()) {
            log.debug("Consulta ao NVD desligada (portscape.nvd.enabled=false)");
            return CveLookupResult.empty();
        }

        Set<String> cpes = distinctCpes(hosts);
        if (cpes.isEmpty()) {
            return CveLookupResult.empty();
        }

        Map<String, List<Cve>> byCpe = new HashMap<>();
        List<String> toFetch = new ArrayList<>();
        for (String cpe : cpes) {
            cache.get(cpe).ifPresentOrElse(cves -> byCpe.put(cpe, cves), () -> toFetch.add(cpe));
        }
        announce(cpes.size(), byCpe.size(), toFetch.size());

        boolean degraded = false;
        for (String cpe : toFetch) {
            try {
                List<Cve> cves = client.findCves(cpe);
                cache.put(cpe, cves);
                byCpe.put(cpe, cves);
            } catch (NvdUnavailableException e) {
                log.warn("Sem CVEs para {} -- o score deste servico fica incompleto: {}",
                        cpe, e.getMessage());
                degraded = true;
            }
        }

        log.info("NVD: {} CPE(s) consultados, {} com CVEs conhecidos{}",
                cpes.size(),
                byCpe.values().stream().filter(list -> !list.isEmpty()).count(),
                degraded ? " (resultado incompleto)" : "");
        return new CveLookupResult(byCpe, degraded);
    }

    /**
     * Diz de antemao quanto tempo isto vai levar.
     *
     * <p>Cada CPE por consultar custa dois pedidos ao NVD (resolver o nome no
     * dicionario e depois pedir os CVEs) e o limitador espaca-os pelo intervalo
     * minimo, por isso uma cache fria pode significar minutos parada aqui. Sem esta
     * linha nos logs, quem esta a ver a barra do scan nao tem forma de saber que a
     * espera e o rate limit do NIST e nao um bloqueio.
     */
    private void announce(int total, int cached, int toFetch) {
        if (toFetch == 0) {
            log.info("NVD: os {} CPE(s) deste scan vieram todos da cache", total);
            return;
        }
        long seconds = toFetch * REQUESTS_PER_CPE * properties.minRequestInterval().toSeconds();
        log.info("NVD: {} de {} CPE(s) em cache; faltam {}, o que leva cerca de {}s a respeitar o rate limit",
                cached, total, toFetch, seconds);
    }

    private static Set<String> distinctCpes(List<Host> hosts) {
        Set<String> cpes = new LinkedHashSet<>();
        for (Host host : hosts) {
            for (Port port : host.ports()) {
                cpes.addAll(port.cpes());
            }
        }
        return cpes;
    }
}
