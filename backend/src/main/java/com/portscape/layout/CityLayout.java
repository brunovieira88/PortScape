package com.portscape.layout;

import java.util.List;
import java.util.Map;

/**
 * A cidade calculada: onde cada host fica e que forma tem o conjunto.
 *
 * @param positions posicao por IP -- inclui os hosts vivos e as ruinas
 * @param districts bairros por ordem, incluindo os vazios
 * @param spacing   distancia entre celulas, para o frontend dimensionar os edificios
 * @param width     extensao em X, do inicio do primeiro bairro ao fim do ultimo
 * @param depth     extensao em Z, a profundidade do bairro mais fundo
 */
public record CityLayout(
        Map<String, HostPosition> positions,
        List<District> districts,
        double spacing,
        double width,
        double depth
) {
    public CityLayout {
        positions = positions == null ? Map.of() : Map.copyOf(positions);
        districts = districts == null ? List.of() : List.copyOf(districts);
    }

    public HostPosition positionOf(String ip) {
        return positions.get(ip);
    }
}
