package com.portscape.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.portscape.baseline.BaselineService;
import com.portscape.config.OpenApiConfig;
import com.portscape.layout.CityLayoutCalculator;
import com.portscape.scan.ScanService;

import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

/**
 * O documento OpenAPI e gerado e cobre os endpoints todos.
 *
 * <p>Um documento gerado falha em silencio: um controller que deixe de aparecer, ou uma
 * capa que nao carregue, nao rebenta nada -- so da uma pagina de documentacao mais
 * pobre, que ninguem repara que empobreceu. Isto poe a geracao debaixo do olho da
 * suite.
 *
 * <p>Corre numa fatia web em vez de arrancar a aplicacao inteira: o springdoc precisa
 * do mapeamento dos controllers e de mais nada, e assim nao arrasta o Postgres para um
 * teste que nao toca na base de dados. As auto-configuracoes dele vem importadas a mao
 * porque uma fatia {@code @WebMvcTest} nao carrega as de bibliotecas de terceiros.
 */
@WebMvcTest(controllers = { ScanController.class, BaselineController.class })
@Import(OpenApiConfig.class)
@ImportAutoConfiguration({ SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class,
        SpringDocConfigProperties.class })
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScanService scanService;

    @MockBean
    private BaselineService baselineService;

    @MockBean
    private CityLayoutCalculator layoutCalculator;

    @Test
    @DisplayName("o documento cobre os endpoints de scans e de baselines")
    void documentCoversEveryEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/scans'].post").exists())
                .andExpect(jsonPath("$.paths['/api/scans'].get").exists())
                .andExpect(jsonPath("$.paths['/api/scans/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/scans/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/scans/{id}/diff'].get").exists())
                .andExpect(jsonPath("$.paths['/api/baselines'].get").exists())
                .andExpect(jsonPath("$.paths['/api/baselines'].post").exists())
                .andExpect(jsonPath("$.paths['/api/baselines'].delete").exists());
    }

    @Test
    @DisplayName("a capa diz que so se analisam redes privadas")
    void coverStatesThePrivateNetworkRule() throws Exception {
        // A regra de etica nao pode viver so no README: quem chega pelo Swagger tem o
        // POST /api/scans a um clique e nunca passou pelo README.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.info.title").value("Portscape API"))
                // A versao vem do pom, filtrada pelo Maven para o application.yml.
                .andExpect(jsonPath("$.info.version").isNotEmpty())
                .andExpect(jsonPath("$.info.description").value(
                        org.hamcrest.Matchers.containsString("private networks")));
    }

    @Test
    @DisplayName("a forma de um host chega ao esquema, e nao so o nome do tipo")
    void hostSchemaCarriesTheFields() throws Exception {
        // O esquema e o que uma pessoa le para saber o que esperar do JSON. Se os DTOs
        // deixarem de ser inspeccionados, isto fica um objecto vazio.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.HostDto.properties.ip").exists())
                .andExpect(jsonPath("$.components.schemas.HostDto.properties.riskScore").exists())
                .andExpect(jsonPath("$.components.schemas.HostDto.properties.portCount").exists())
                .andExpect(jsonPath("$.components.schemas.PortDto.properties.number").exists());
    }
}
