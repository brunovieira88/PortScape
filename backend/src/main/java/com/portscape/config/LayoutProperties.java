package com.portscape.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geometria da cidade 3D.
 *
 * @param gridWidth   quantas colunas tem cada bairro. 16 faz de um /24 uma malha
 *                    16x16 arrumada, e a coluna sai do resto da divisao do indice do
 *                    host -- por isso mudar isto muda a coluna de todos os hosts
 * @param spacing     distancia entre centros de celulas, em unidades de mundo
 * @param districtGap colunas vazias entre bairros, para as zonas se lerem separadas
 */
@ConfigurationProperties(prefix = "portscape.layout")
public record LayoutProperties(
        Integer gridWidth,
        Double spacing,
        Integer districtGap
) {
    public LayoutProperties {
        gridWidth = gridWidth == null || gridWidth < 1 ? 16 : gridWidth;
        spacing = spacing == null || spacing <= 0 ? 4.0 : spacing;
        districtGap = districtGap == null || districtGap < 0 ? 4 : districtGap;
    }

    /** Passo entre o inicio de dois bairros consecutivos, em colunas. */
    public int districtStride() {
        return gridWidth + districtGap;
    }
}
