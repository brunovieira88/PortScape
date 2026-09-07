package com.portscape.domain;

import java.util.List;

import com.portscape.risk.nvd.Cve;

/**
 * Uma porta detetada num host. {@code service}, {@code product} e {@code version}
 * podem ser null: o nmap nem sempre consegue identificar o servico.
 *
 * <p>{@code cpes} sao os identificadores CPE que o nmap atribui ao servico
 * (ex. {@code cpe:/a:openbsd:openssh:9.6}) e sao a chave da consulta de CVEs ao NVD.
 *
 * <p>{@code cves} sao as falhas conhecidas do que esta a correr aqui, ja resolvidas a
 * partir desses CPEs. Vem vazias da fase de scan e sao preenchidas pelo
 * {@link com.portscape.risk.nvd.PortCveEnricher} -- ate porque so ha CVEs depois de
 * haver versao, e a versao so aparece na segunda passagem do nmap.
 *
 * <p><b>Porque e que {@code cveTotal} existe.</b> A lista e truncada
 * ({@code portscape.nvd.max-cves-per-port}): um CPE de kernel pode devolver milhares
 * de CVEs, e um scan que os arraste todos enche o JSON e a base de dados. Sem guardar
 * o total, mostrar 25 seria indistinguivel de mostrar 25 de 431 -- e essa e a
 * diferenca entre truncar e mentir.
 *
 * <p>O dominio depende de {@code risk.nvd} pela mesma razao que o {@link Host} depende
 * de {@code risk.RiskScore}: o que o scan encontrou inclui o que se sabe sobre o que
 * encontrou.
 */
public record Port(
        int number,
        String protocol,
        String state,
        String service,
        String product,
        String version,
        List<String> cpes,
        List<Cve> cves,
        int cveTotal
) {
    public Port {
        cpes = cpes == null ? List.of() : List.copyOf(cpes);
        cves = cves == null ? List.of() : List.copyOf(cves);
    }

    /** Porta acabada de sair do nmap: tem CPEs, ainda nao tem CVEs. */
    public Port(int number, String protocol, String state,
                String service, String product, String version, List<String> cpes) {
        this(number, protocol, state, service, product, version, cpes, List.of(), 0);
    }

    /** Porta sem CPEs -- o caso da fase de descoberta, que corre sem {@code -sV}. */
    public Port(int number, String protocol, String state,
                String service, String product, String version) {
        this(number, protocol, state, service, product, version, List.of());
    }

    /**
     * A mesma porta com as falhas conhecidas anexadas.
     *
     * @param found    os CVEs a mostrar, ja ordenados e truncados
     * @param total    quantos existiam antes de truncar
     */
    public Port withCves(List<Cve> found, int total) {
        return new Port(number, protocol, state, service, product, version, cpes, found, total);
    }
}
