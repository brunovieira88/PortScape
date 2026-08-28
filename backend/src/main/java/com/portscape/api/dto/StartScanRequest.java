package com.portscape.api.dto;

/**
 * @param target subnet ou IP a analisar. Opcional -- se vier vazio usa-se
 *               {@code portscape.nmap.default-target}. A validacao propriamente dita
 *               esta no {@link com.portscape.scan.TargetValidator}, nao em anotacoes:
 *               a regra "so redes privadas" e logica de dominio, nao formato.
 */
public record StartScanRequest(String target) {
}
