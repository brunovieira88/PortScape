package com.portscape.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portscape.baseline.HostChange;
import com.portscape.domain.Host;

import com.portscape.risk.RiskBand;

/**
 * O {@code mac} e o {@code vendor} podem vir a null: so ha endereco fisico para
 * maquinas no mesmo segmento e com o scan privilegiado. O {@code vendor} e o unico
 * campo que diz <i>o que</i> a maquina e ("Apple, Inc."), e nao so onde esta.
 *
 * <p>{@code portCount} vai explicito no JSON: e dele que sai a altura do edificio na
 * cena 3D (fase 4) e evita que o frontend tenha de contar. O {@code riskScore} da a
 * cor, e as {@code riskReasons} enchem o painel de detalhes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HostDto(
        String ip,
        String mac,
        String vendor,
        String hostname,
        String osGuess,
        Integer osAccuracy,
        int portCount,
        Integer riskScore,
        RiskBand riskBand,
        PositionDto position,
        List<RiskReasonDto> riskReasons,
        HostChange change,
        boolean isNew,
        boolean isChanged,
        List<PortDto> ports
) {
    public static HostDto from(Host host) {
        return from(host, HostChange.UNKNOWN, null, null);
    }

    public static HostDto from(Host host, HostChange change) {
        return from(host, change, null, null);
    }

    /**
     * {@code isNew} e {@code isChanged} sao redundantes face a {@code change}, mas
     * sao o que a cena 3D consome: um booleano por destaque visual evita comparar
     * strings dentro do loop de render.
     */
    public static HostDto from(Host host, HostChange change, RiskBand riskBand, PositionDto position) {
        return new HostDto(
                host.ip(),
                host.mac(),
                host.vendor(),
                host.hostname(),
                host.osGuess(),
                host.osAccuracy(),
                host.portCount(),
                host.risk() == null ? null : host.risk().score(),
                riskBand,
                position,
                host.risk() == null ? List.of()
                        : host.risk().reasons().stream().map(RiskReasonDto::from).toList(),
                change,
                change == HostChange.NEW,
                change == HostChange.CHANGED,
                host.ports().stream().map(PortDto::from).toList());
    }
}
