package com.portscape.api.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portscape.baseline.HostChange;
import com.portscape.baseline.ScanDiff;
import com.portscape.layout.CityLayout;

/**
 * Comparacao de um scan com o baseline da sua rede.
 *
 * @param disappeared hosts que existiam no baseline e ja nao respondem. Nao aparecem
 *                    na cidade 3D -- nao ha edificio para um host que sumiu -- mas numa
 *                    auditoria sao tao relevantes como os novos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScanDiffResponse(
        String scanId,
        String baselineScanId,
        Map<String, HostChange> changeByIp,
        List<HostDto> disappeared
) {
    public static ScanDiffResponse from(String scanId, ScanDiff diff, CityLayout layout) {
        return new ScanDiffResponse(
                scanId,
                diff.baselineScanId() == null ? null : diff.baselineScanId().toString(),
                diff.changeByIp(),
                diff.disappeared().stream().map(host -> {
                    var position = layout == null ? null : layout.positions().get(host.ip());
                    var posDto = position == null ? null : new PositionDto(position.x(), position.z());
                    var band = position == null ? null : position.band();
                    return HostDto.from(host, diff.changeFor(host.ip()), band, posDto);
                }).toList());
    }
}
