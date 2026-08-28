package com.portscape.domain;

/**
 * Uma porta detetada num host. {@code service}, {@code product} e {@code version}
 * podem ser null: o nmap nem sempre consegue identificar o serviço.
 */
public record Port(
        int number,
        String protocol,
        String state,
        String service,
        String product,
        String version
) {
}
