package com.portscape.api.dto;

import java.util.List;

import com.portscape.domain.Port;

/**
 * Uma porta aberta, com o que se sabe do que la esta a correr.
 *
 * <p><b>Sem {@code @JsonInclude(NON_NULL)}</b>, ao contrario do {@link HostDto}: os
 * campos nulos de uma porta chegam ao cliente como {@code null} explicito, e o
 * {@code frontend/src/api/types.ts} ja conta com isso. E deliberado -- um
 * {@code product: null} diz "o nmap nao identificou", enquanto um campo ausente e
 * indistinguivel de um campo que a API deixou de mandar.
 *
 * <p>{@code cves} vem truncada em {@code portscape.nvd.max-cves-per-port} e ordenada
 * do pior CVSS para o melhor; {@code cveTotal} diz quantos existiam antes de truncar.
 * Quando os dois diferem, o cliente tem de o dizer ao utilizador.
 */
public record PortDto(
        int number,
        String protocol,
        String state,
        String service,
        String product,
        String version,
        List<String> cpes,
        List<CveDto> cves,
        int cveTotal
) {
    public static PortDto from(Port port) {
        return new PortDto(port.number(), port.protocol(), port.state(),
                port.service(), port.product(), port.version(), port.cpes(),
                port.cves().stream().map(CveDto::from).toList(), port.cveTotal());
    }
}
