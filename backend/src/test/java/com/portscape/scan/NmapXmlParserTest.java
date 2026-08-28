package com.portscape.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.domain.Host;
import com.portscape.domain.Port;
import com.portscape.scan.exception.NmapXmlParseException;

class NmapXmlParserTest {

    private NmapXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new NmapXmlParser();
    }

    private static String load(String resource) throws IOException {
        try (InputStream in = NmapXmlParserTest.class.getResourceAsStream("/" + resource)) {
            assertThat(in).as("recurso de teste %s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("le um XML real do nmap com DOCTYPE, hosthint, extraports e cpe")
    void parsesRealNmapOutput() throws IOException {
        List<Host> hosts = parser.parse(load("sample-scan.xml"));

        // O <hosthint> repete o 192.168.1.1 mas nao e um <host>: nao pode duplicar.
        assertThat(hosts).extracting(Host::ip).containsExactly("192.168.1.1", "192.168.1.42");
    }

    @Test
    @DisplayName("mapeia hostname, servico, produto e versao")
    void mapsHostAndServiceDetails() throws IOException {
        List<Host> hosts = parser.parse(load("sample-scan.xml"));

        Host router = hosts.get(0);
        assertThat(router.hostname()).isEqualTo("router.lan");
        assertThat(router.ports()).extracting(Port::number).containsExactly(23, 80);

        Port http = router.ports().get(1);
        assertThat(http.service()).isEqualTo("http");
        assertThat(http.product()).isEqualTo("lighttpd");
        assertThat(http.version()).isEqualTo("1.4.59");
        assertThat(http.protocol()).isEqualTo("tcp");
    }

    @Test
    @DisplayName("descarta hosts down e portas que nao estao abertas")
    void filtersDownHostsAndClosedPorts() throws IOException {
        List<Host> hosts = parser.parse(load("sample-scan.xml"));

        assertThat(hosts).extracting(Host::ip).doesNotContain("192.168.1.99");
        // A 443 vem no XML com state="closed" mesmo tendo sido usado --open.
        assertThat(hosts.get(0).ports()).extracting(Port::number).doesNotContain(443);
    }

    @Test
    @DisplayName("escolhe o palpite de OS com maior accuracy")
    void picksMostAccurateOsMatch() throws IOException {
        Host router = parser.parse(load("sample-scan.xml")).get(0);

        // No XML o osmatch de 90 aparece antes do de 94: a ordem nao pode decidir.
        assertThat(router.osGuess()).isEqualTo("Linux 5.4 - 5.15");
        assertThat(router.osAccuracy()).isEqualTo(94);
    }

    @Test
    @DisplayName("host sem bloco <os> e sem hostname fica com campos a null")
    void handlesHostWithoutOsOrHostname() throws IOException {
        Host host = parser.parse(load("sample-scan.xml")).get(1);

        assertThat(host.osGuess()).isNull();
        assertThat(host.osAccuracy()).isNull();
        assertThat(host.hostname()).isNull();
    }

    @Test
    @DisplayName("lista de um so elemento continua a ser lista (armadilha do Jackson XML)")
    void handlesSingleElementLists() throws IOException {
        Host host = parser.parse(load("sample-scan.xml")).get(1);

        assertThat(host.ports()).hasSize(1);
        assertThat(host.portCount()).isEqualTo(1);
        assertThat(host.ports().get(0).number()).isEqualTo(22);
    }

    @Test
    @DisplayName("scan sem hosts nao e erro")
    void emptyScanIsNotAnError() throws IOException {
        assertThat(parser.parse(load("empty-scan.xml"))).isEmpty();
    }

    @Test
    @DisplayName("nao resolve entidades externas (XXE)")
    void doesNotResolveExternalEntities() throws IOException {
        String xml = load("xxe-scan.xml");

        // Aceitavel: rejeitar o documento, ou le-lo sem nunca ler o ficheiro externo.
        try {
            List<Host> hosts = parser.parse(xml);
            assertThat(hosts).allSatisfy(host ->
                    assertThat(host.hostname()).doesNotContain("root:"));
        } catch (NmapXmlParseException expected) {
            assertThat(expected).hasMessageContaining("XML");
        }
    }

    @Test
    @DisplayName("XML malformado da NmapXmlParseException")
    void malformedXmlFails() {
        assertThatThrownBy(() -> parser.parse("<nmaprun><host><status state=\"up\""))
                .isInstanceOf(NmapXmlParseException.class);
    }

    @Test
    @DisplayName("XML vazio da NmapXmlParseException")
    void blankXmlFails() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(NmapXmlParseException.class)
                .hasMessageContaining("nao devolveu XML");
    }
}
