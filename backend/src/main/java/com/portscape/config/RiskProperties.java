package com.portscape.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pesos do scoring de risco. Em configuracao e nao no codigo: os pesos sao um juizo
 * editorial que se afina com a experiencia, nao uma constante do universo.
 *
 * @param portWeights        pontos por porta aberta, por numero de porta
 * @param defaultPortWeight  pontos de uma porta aberta que nao esta na tabela
 * @param cvssMultiplier     pontos por unidade de CVSS do pior CVE do host
 * @param extraSevereCvePoints pontos por cada CVE grave adicional
 * @param maxExtraSevereCvePoints tecto do somatorio anterior
 * @param severeCvssThreshold a partir de que CVSS um CVE conta como "grave"
 * @param unknownHostPoints  pontos por o host nao existir no baseline
 * @param newPortPoints      pontos por cada porta que nao existia no baseline
 * @param maxNewPortPoints   tecto do somatorio anterior
 */
@ConfigurationProperties(prefix = "portscape.risk")
public record RiskProperties(
        Map<Integer, Integer> portWeights,
        Integer defaultPortWeight,
        Double cvssMultiplier,
        Integer extraSevereCvePoints,
        Integer maxExtraSevereCvePoints,
        Double severeCvssThreshold,
        Integer unknownHostPoints,
        Integer newPortPoints,
        Integer maxNewPortPoints
) {
    public RiskProperties {
        portWeights = portWeights == null ? Map.of() : Map.copyOf(portWeights);
        defaultPortWeight = defaultPortWeight == null ? 8 : defaultPortWeight;
        cvssMultiplier = cvssMultiplier == null ? 4.0 : cvssMultiplier;
        extraSevereCvePoints = extraSevereCvePoints == null ? 3 : extraSevereCvePoints;
        maxExtraSevereCvePoints = maxExtraSevereCvePoints == null ? 15 : maxExtraSevereCvePoints;
        severeCvssThreshold = severeCvssThreshold == null ? 7.0 : severeCvssThreshold;
        unknownHostPoints = unknownHostPoints == null ? 25 : unknownHostPoints;
        newPortPoints = newPortPoints == null ? 8 : newPortPoints;
        maxNewPortPoints = maxNewPortPoints == null ? 24 : maxNewPortPoints;
    }

    public int weightFor(int port) {
        return portWeights.getOrDefault(port, defaultPortWeight);
    }
}
