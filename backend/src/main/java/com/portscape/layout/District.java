package com.portscape.layout;

import com.portscape.risk.RiskBand;

/**
 * Um bairro: a zona da cidade que agrupa os hosts de uma faixa de risco.
 *
 * <p>Vai no JSON com as suas dimensoes para o frontend poder desenhar a placa de chao
 * por baixo, sem ter de reconstituir a geometria a partir das posicoes dos hosts.
 *
 * @param hostCount quantos hosts la estao -- nunca zero: as faixas sem hosts nao
 *                  chegam a ter bairro (ver {@link CityLayoutCalculator})
 */
public record District(RiskBand band, double x, double width, double depth, int hostCount) {
}
