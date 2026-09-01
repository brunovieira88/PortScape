package com.portscape.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quanto tempo para tras conta como "conhecido" nesta rede.
 *
 * @param window janela do inventario. Um dispositivo visto dentro dela e conhecido:
 *               se nao responder ao scan de agora aparece como offline, e se voltar
 *               nao e assinalado como novo. Passada a janela sem dar sinal, sai da
 *               cidade e fica so no historico.
 *
 *               <p>E medida em <b>tempo</b> e nao em numero de scans de proposito: os
 *               scans nao sao regulares -- podem ser oito numa noite e nenhum durante
 *               dois dias -- e "os ultimos cinco scans" tanto pode ser meia hora como
 *               uma semana.
 */
@ConfigurationProperties(prefix = "portscape.baseline")
public record BaselineProperties(Duration window) {

    public BaselineProperties {
        window = window == null || window.isNegative() || window.isZero()
                ? Duration.ofDays(7)
                : window;
    }
}
