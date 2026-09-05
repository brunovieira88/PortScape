package com.portscape.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portscape.risk.nvd.Cve;

/**
 * Uma falha conhecida do servico desta porta.
 *
 * <p>{@code vector} vai por traduzir de proposito: a traducao para linguagem corrente
 * ("alcancavel pela rede, sem autenticacao") e apresentacao, e quem a faz e o
 * frontend, que tem de saber explica-la tambem no modo demo, onde nao ha backend.
 *
 * <p>{@code url} e derivado do id e nao guardado -- e sempre a mesma pagina do NIST, e
 * gravar uma copia por CVE seria guardar a mesma string milhares de vezes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CveDto(
        String id,
        Double cvssScore,
        String severity,
        String vector,
        Instant published,
        String description,
        String url,
        KevDto kev
) {
    private static final String NVD_DETAIL = "https://nvd.nist.gov/vuln/detail/";

    public static CveDto from(Cve cve) {
        return new CveDto(cve.id(), cve.cvssScore(), cve.severity(), cve.vector(),
                cve.published(), cve.description(), NVD_DETAIL + cve.id(),
                KevDto.from(cve.kev()));
    }
}
