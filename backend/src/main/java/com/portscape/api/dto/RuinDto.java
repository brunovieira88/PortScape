package com.portscape.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portscape.baseline.HostChange;
import com.portscape.risk.RiskBand;
import com.portscape.layout.HostPosition;
import com.portscape.domain.Host;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuinDto(
        String ip,
        String hostname,
        String osGuess,
        RiskBand riskBand,
        PositionDto position,
        HostChange change
) {
    public static RuinDto from(Host host, HostPosition pos) {
        return new RuinDto(
                pos.ip(),
                host.hostname(),
                host.osGuess(),
                pos.band(),
                new PositionDto(pos.x(), pos.z()),
                HostChange.DISAPPEARED
        );
    }
}
