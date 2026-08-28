package com.portscape.persistence;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Postgres real para os testes de persistencia.
 *
 * <p>Container estatico e partilhado por todas as classes que estendam isto, sem
 * {@code @Testcontainers}/{@code @Container}: essa anotacao arrancaria um Postgres
 * novo por classe. O Ryuk trata de o remover no fim da JVM.
 *
 * <p>Postgres real e nao H2: o Flyway corre as mesmas migracoes que correm em
 * producao, por isso um teste verde aqui significa que o schema funciona mesmo.
 */
@ActiveProfiles("test")
public abstract class PostgresTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
