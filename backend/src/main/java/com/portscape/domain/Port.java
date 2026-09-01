package com.portscape.domain;

import java.util.List;

/**
 * Uma porta detetada num host. {@code service}, {@code product} e {@code version}
 * podem ser null: o nmap nem sempre consegue identificar o servico.
 *
 * <p>{@code cpes} sao os identificadores CPE que o nmap atribui ao servico
 * (ex. {@code cpe:/a:openbsd:openssh:9.6}) e sao a chave da consulta de CVEs ao NVD.
 */
public record Port(
        int number,
        String protocol,
        String state,
        String service,
        String product,
        String version,
        List<String> cpes
) {
    public Port {
        cpes = cpes == null ? List.of() : List.copyOf(cpes);
    }

    /** Porta sem CPEs -- o caso da fase de descoberta, que corre sem {@code -sV}. */
    public Port(int number, String protocol, String state,
                String service, String product, String version) {
        this(number, protocol, state, service, product, version, List.of());
    }
}
