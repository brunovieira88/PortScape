package com.portscape.api.dto;

import java.util.List;

import com.portscape.domain.Host;

/**
 * {@code portCount} vai explicito no JSON: e dele que sai a altura do edificio na
 * cena 3D (fase 4) e evita que o frontend tenha de contar.
 */
public record HostDto(
        String ip,
        String hostname,
        String osGuess,
        Integer osAccuracy,
        int portCount,
        List<PortDto> ports
) {
    public static HostDto from(Host host) {
        return new HostDto(
                host.ip(),
                host.hostname(),
                host.osGuess(),
                host.osAccuracy(),
                host.portCount(),
                host.ports().stream().map(PortDto::from).toList());
    }
}
