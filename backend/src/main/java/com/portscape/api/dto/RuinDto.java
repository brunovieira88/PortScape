package com.portscape.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portscape.baseline.HostChange;
import com.portscape.risk.RiskBand;
import com.portscape.layout.HostPosition;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuinDto(
        String ip,
        RiskBand riskBand,
        PositionDto position,
        HostChange change
) {
    public static RuinDto from(HostPosition pos) {
        return new RuinDto(
                pos.ip(),
                pos.band(),
                new PositionDto(pos.x(), pos.z()),
                HostChange.DISAPPEARED
        );
    }
}
