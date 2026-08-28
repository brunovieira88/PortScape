package com.portscape.risk;

import java.util.List;

/**
 * Uma regra de risco. Cada implementacao olha para um aspeto do host e devolve as
 * razoes que encontrar -- ou nada, se nao tiver nada a dizer.
 *
 * <p>E aqui que vive a logica propria do projeto: o nmap diz o que esta aberto, o
 * Portscape diz o que isso significa.
 */
public interface RiskRule {

    List<RiskReason> evaluate(RiskInput input);
}
