package com.portscape.layout;

import com.portscape.risk.RiskBand;

/**
 * Onde o edificio de um host fica na cidade.
 *
 * <p>Nao ha {@code y}: e sempre 0. A altura do edificio vem do numero de portas
 * abertas e e o frontend que a aplica -- o backend nao decide escalas visuais.
 */
public record HostPosition(String ip, RiskBand band, double x, double z) {
}
