package com.portscape.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aplica {@code portscape.nvd.timeout} ao cliente HTTP.
 *
 * <p>Sem isto o {@code RestClient} herda os timeouts do JDK, que nao tem limite de
 * leitura: um NVD que aceita a ligacao e nunca responde ficaria a segurar o thread
 * indefinidamente. E o pool de scans tem <b>um unico thread</b> (ver
 * {@link AsyncConfig}), por isso um pedido pendurado nao pararia so o scan em curso
 * -- parava a fila toda.
 *
 * <p>Feito com um {@link RestClientCustomizer} e nao dentro do
 * {@code NvdClient}: assim o cliente trata de politica do NVD (baseUrl, api key,
 * dois pedidos) e nao de canalizacao HTTP, e os testes continuam a poder instalar
 * o seu proprio {@code MockRestServiceServer} no builder.
 */
@Configuration
public class NvdHttpConfig {

    @Bean
    public RestClientCustomizer nvdTimeoutCustomizer(NvdProperties properties) {
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(properties.timeout())
                        .withReadTimeout(properties.timeout())));
    }
}
