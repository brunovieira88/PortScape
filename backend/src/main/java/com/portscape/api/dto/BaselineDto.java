package com.portscape.api.dto;

import java.time.Instant;

import com.portscape.baseline.Baseline;

public record BaselineDto(String target, String scanId, Instant pinnedAt) {

    public static BaselineDto from(Baseline baseline) {
        return new BaselineDto(baseline.target(), baseline.scanId().toString(), baseline.pinnedAt());
    }
}
