package com.portscape.api.dto;

import com.portscape.domain.Port;

public record PortDto(
        int number,
        String protocol,
        String state,
        String service,
        String product,
        String version
) {
    public static PortDto from(Port port) {
        return new PortDto(port.number(), port.protocol(), port.state(),
                port.service(), port.product(), port.version());
    }
}
