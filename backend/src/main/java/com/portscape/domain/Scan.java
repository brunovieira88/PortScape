package com.portscape.domain;

import java.time.Instant;
import java.util.List;

/**
 * Resultado do parsing de um scan do nmap. Imutavel: o estado mutavel do job
 * vive no {@link com.portscape.scan.ScanJob}.
 */
public record Scan(
        String target,
        Instant startedAt,
        Instant finishedAt,
        List<Host> hosts
) {
    public Scan {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
    }

    public int hostsUp() {
        return hosts.size();
    }
}
