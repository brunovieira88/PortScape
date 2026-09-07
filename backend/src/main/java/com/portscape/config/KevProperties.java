package com.portscape.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalogo KEV (Known Exploited Vulnerabilities) da CISA.
 *
 * <p>O CVSS diz <i>quao grave seria</i>; o KEV diz <i>que esta a acontecer</i>. Sao
 * eixos diferentes, e uma falha 7.5 em campanhas de ransomware hoje e mais urgente do
 * que uma 9.8 teorica de 2015 que ninguem nunca explorou.
 *
 * <p>Ao contrario do NVD, o feed e um unico ficheiro publico, sem chave e sem rate
 * limit -- por isso nao ha aqui nem cache em base de dados nem limitador de pedidos,
 * so um intervalo de recarga.
 *
 * @param enabled         desliga a consulta; os CVEs continuam a aparecer, apenas sem
 *                        a indicacao de exploracao ativa
 * @param feedUrl         URL do catalogo, parametrizado para os testes apontarem a um
 *                        servidor falso
 * @param timeout         tempo maximo do pedido -- o scan nunca deve ficar preso a
 *                        espera da CISA
 * @param refreshInterval de quanto em quanto tempo se volta a buscar o catalogo. A
 *                        CISA publica no maximo uma vez por dia
 */
@ConfigurationProperties(prefix = "portscape.kev")
public record KevProperties(
        boolean enabled,
        String feedUrl,
        Duration timeout,
        Duration refreshInterval
) {
    public KevProperties {
        feedUrl = feedUrl == null || feedUrl.isBlank()
                ? "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
                : feedUrl;
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        refreshInterval = refreshInterval == null ? Duration.ofDays(1) : refreshInterval;
    }
}
