package com.portscape.domain;

import java.util.List;

/**
 * Um host que respondeu ao scan. {@code hostname} e {@code osGuess} podem ser null.
 */
public record Host(
        String ip,
        String hostname,
        String osGuess,
        Integer osAccuracy,
        List<Port> ports
) {
    public Host {
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    /** Altura do edificio na cena 3D deriva daqui (ver fase 4). */
    public int portCount() {
        return ports.size();
    }
}
