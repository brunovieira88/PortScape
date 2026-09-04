package com.portscape.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A capa do OpenAPI. Os endpoints e os campos sao lidos dos controllers e dos DTOs.
 *
 * <p>A restricao a redes privadas fica na descricao da API e nao so no README: quem
 * chega aqui pelo {@code /swagger-ui.html} tem um {@code POST /api/scans} a um clique
 * de distancia, e tem de encontrar a regra no mesmo sitio onde encontra o botao.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portscapeOpenApi(@Value("${portscape.version:0.1.0-SNAPSHOT}") String version) {
        return new OpenAPI().info(new Info()
                .title("Portscape API")
                .version(version)
                .description("""
                        Turns an nmap scan of a local network into a scored, comparable \
                        inventory: hosts, open ports, a risk score with its reasons, and \
                        what changed since the baseline.

                        Portscape only scans private networks (RFC 1918). A target \
                        outside one is rejected with `INVALID_TARGET` -- this is enforced \
                        in `TargetValidator`, not left to the caller's good judgement.""")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
