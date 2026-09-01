package com.portscape.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geometria da cidade 3D.
 *
 * @param gridWidth   largura maxima de um bairro, em colunas. Um bairro e um bloco
 *                    quase quadrado ({@code ceil(sqrt(n))} colunas); este valor so
 *                    entra quando isso passaria de 16, para um bairro muito povoado
 *                    nao se esticar ate a cidade deixar de se ver de uma vez
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
