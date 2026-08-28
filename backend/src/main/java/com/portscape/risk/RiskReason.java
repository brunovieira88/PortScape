package com.portscape.risk;

/**
 * Uma parcela do score, com a justificacao.
 *
 * <p>Guardar razoes estruturadas e nao so um numero e o que permite ao painel de
 * detalhes responder "porque e que este host tem 78?" -- e o que torna o scoring
 * auditavel em vez de uma caixa preta.
 *
 * @param code        identificador estavel da regra, para o frontend agrupar/traduzir
 * @param description texto legivel, ja com os valores concretos
 * @param points      quanto esta razao somou ao score
 */
public record RiskReason(String code, String description, int points) {
}
