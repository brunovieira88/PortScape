package com.portscape.api.dto;

import java.util.List;

import com.portscape.domain.Port;

public record PortDto(
        int number,
        String protocol,
        String state,
        String service,
        String product,
        String version,
        List<String> cpes
) {
    public static PortDto from(Port port) {
        return new PortDto(port.number(), port.protocol(), port.state(),
                port.service(), port.product(), port.version(), port.cpes());
    }
}
