package com.portscape.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geometria da cidade 3D.
 *
 * @param gridWidth   ao fim de quantas colunas o indice do host passa a linha
 *                    seguinte. 16 da a um /24 a forma de uma malha 16x16 antes de ser
 *                    compactada, e e o que decide que hosts partilham coluna
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
}
