package com.portscape.domain;

import java.util.List;

import com.portscape.risk.RiskScore;

/**
 * Um host que respondeu ao scan. {@code hostname} e {@code osGuess} podem ser null.
 *
 * <p>{@code risk} e null enquanto o scan corre e so e preenchido no fim, quando ja
 * ha CVEs e baseline com que pontuar.
 */
public record Host(
        String ip,
        String hostname,
        String osGuess,
        Integer osAccuracy,
        List<Port> ports,
        RiskScore risk
) {
    public Host {
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    /** Host ainda sem risco calculado -- e o que sai do parser do nmap. */
    public Host(String ip, String hostname, String osGuess, Integer osAccuracy, List<Port> ports) {
        this(ip, hostname, osGuess, osAccuracy, ports, null);
    }

    public Host withRisk(RiskScore risk) {
        return new Host(ip, hostname, osGuess, osAccuracy, ports, risk);
    }

    /** Altura do edificio na cena 3D deriva daqui (ver fase 4). */
    public int portCount() {
        return ports.size();
    }
}
