package com.portscape.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * O cliente HTTP do catalogo KEV, com o seu proprio timeout.
 *
 * <p>Nao pode ser um {@link org.springframework.boot.web.client.RestClientCustomizer}
 * como o do NVD ({@link NvdHttpConfig}): um customizer aplica-se a <b>todos</b> os
 * builders, e o KEV e o NVD querem timeouts diferentes -- o KEV descarrega um ficheiro
 * de varios MB, o NVD faz pedidos de API. Dois customizers globais dariam um vencedor
 * arbitrario consoante a ordem em que o Spring os aplicasse.
 *
 * <p>E fica aqui, e nao dentro do {@link com.portscape.risk.kev.KevCatalog}, pela mesma
 * razao que o do NVD: o catalogo trata do que sabe (o feed, o intervalo de recarga) e
 * nao de canalizacao HTTP -- e assim os testes podem instalar o seu proprio
 * {@code MockRestServiceServer} sem que o construtor lho arranque debaixo dos pes.
 */
@Configuration
public class KevHttpConfig {

    public static final String KEV_REST_CLIENT = "kevRestClient";

    @Bean(KEV_REST_CLIENT)
    public RestClient kevRestClient(RestClient.Builder builder, KevProperties properties) {
        return builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(properties.timeout())
                                .withReadTimeout(properties.timeout())))
                .build();
    }
}
