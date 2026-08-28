package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Testa a deteccao contra a rede real da maquina que corre os testes -- nao ha
 * forma pratica de mockar "a rota por defeito do SO" sem reimplementar o proprio
 * SO. O teste assume que a maquina de CI/desenvolvimento tem pelo menos uma
 * interface com rota por defeito, o que e o caso normal.
 */
class LocalNetworkDetectorTest {

    private static final Pattern CIDR = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}$");

    @Test
    void detectsAValidCidrOrGivesUpCleanly() {
        Optional<String> subnet = new LocalNetworkDetector().detectLocalSubnet();

        // Em CI sem rede o resultado legitimo e vazio -- so validamos a forma quando ha algo.
        assumeTrue(subnet.isPresent(), "sem rota por defeito nesta maquina, a saltar");
        assertThat(subnet.get()).matches(CIDR);
    }
}
