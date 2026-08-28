package com.portscape.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consulta de CVEs ao NVD (National Vulnerability Database do NIST).
 *
 * @param enabled             desliga a consulta por completo; o scoring continua a
 *                            funcionar so com as regras de portas
 * @param baseUrl             raiz da API, parametrizada para os testes apontarem a um
 *                            servidor falso em vez da API publica
 * @param apiKey              opcional. Sem key o NVD permite 5 pedidos / 30s; com key,
 *                            50. Ler de {@code PORTSCAPE_NVD_API_KEY}
 * @param timeout             tempo maximo por pedido -- o scan nunca deve ficar preso
 *                            a espera do NVD
 * @param minRequestInterval  intervalo minimo entre pedidos, para respeitar o rate
 *                            limit sem levar 429
 * @param cacheTtl            durante quanto tempo uma resposta guardada continua a
 *                            valer. E a cache que torna isto viavel: entre scans da
 *                            mesma rede, quase todos os CPEs ja la estao
 * @param emptyCacheTtl       TTL mais curto para respostas <b>sem</b> CVEs. "Nao
 *                            encontrei nada" e a resposta que tanto sai de um produto
 *                            realmente sem vulnerabilidades como de um nome que o NVD
 *                            nao reconheceu -- guardar isso uma semana esconderia o
 *                            segundo caso durante uma semana
 */
@ConfigurationProperties(prefix = "portscape.nvd")
public record NvdProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        Duration timeout,
        Duration minRequestInterval,
        Duration cacheTtl,
        Duration emptyCacheTtl
) {
    public NvdProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://services.nvd.nist.gov" : baseUrl;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        minRequestInterval = minRequestInterval == null ? Duration.ofSeconds(6) : minRequestInterval;
        cacheTtl = cacheTtl == null ? Duration.ofDays(7) : cacheTtl;
        emptyCacheTtl = emptyCacheTtl == null ? Duration.ofDays(1) : emptyCacheTtl;
        apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
    }

    public boolean hasApiKey() {
        return apiKey != null;
    }
}
