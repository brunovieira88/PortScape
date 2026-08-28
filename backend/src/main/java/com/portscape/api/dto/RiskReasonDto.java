package com.portscape.api.dto;

import com.portscape.risk.RiskReason;

public record RiskReasonDto(String code, String description, int points) {

    public static RiskReasonDto from(RiskReason reason) {
        return new RiskReasonDto(reason.code(), reason.description(), reason.points());
    }
}
