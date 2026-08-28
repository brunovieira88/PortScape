package com.portscape.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.portscape.config.LayoutProperties;
import com.portscape.domain.Host;
import com.portscape.risk.RiskBand;
import com.portscape.risk.RiskFixtures;
import com.portscape.risk.RiskScore;

class CityLayoutCalculatorTest {

    private static final String TARGET = "192.168.1.0/24";
    /** Grelha 16 colunas, celulas de 4 unidades, 4 colunas de intervalo entre bairros. */
    private static final LayoutProperties LAYOUT = new LayoutProperties(16, 4.0, 4);

    private final CityLayoutCalculator calculator =
            new CityLayoutCalculator(LAYOUT, RiskFixtures.PROPERTIES);

    /** Um host com um score concreto, para cair na faixa que o teste quer. */
    private static Host host(String ip, Integer score) {
        return new Host(ip, null, null, null, List.of(),
                score == null ? null : new RiskScore(score, List.of()));
    }

    private CityLayout layoutOf(Host... hosts) {
        return calculator.calculate(TARGET, List.of(hosts), List.of());
    }

    private static double districtX(RiskBand band) {
        return band.ordinal() * (16 + 4) * 4.0;
    }

    @Test
    @DisplayName("a faixa da o bairro e o IP da o lugar dentro dele")
    void placesAHostInItsBandDistrictAtItsOwnCell() {
        HostPosition position = layoutOf(host("192.168.1.254", 100)).positionOf("192.168.1.254");

        assertThat(position.band()).isEqualTo(RiskBand.CRITICAL);
        // 254 % 16 = 14 -> coluna 14 dentro do bairro CRITICAL (que comeca em x=0)
        assertThat(position.x()).isEqualTo(districtX(RiskBand.CRITICAL) + 14 * 4.0);
    }

    @Test
    @DisplayName("dois scans com a mesma composicao dao exatamente as mesmas posicoes")
    void isStableAcrossScans() {
        Host[] hosts = {host("192.168.1.1", 90), host("192.168.1.73", 60), host("192.168.1.104", 10)};

        assertThat(layoutOf(hosts).positions()).isEqualTo(layoutOf(hosts).positions());
    }

    @Test
    @DisplayName("a coluna de um host nunca muda, mesmo quando ele muda de bairro")
    void keepsTheColumnEvenWhenTheBandChanges() {
        double low = layoutOf(host("192.168.1.254", 10)).positionOf("192.168.1.254").x();
        double critical = layoutOf(host("192.168.1.254", 100)).positionOf("192.168.1.254").x();

        assertThat(low - districtX(RiskBand.LOW)).isEqualTo(critical - districtX(RiskBand.CRITICAL));
    }

    @Test
    @DisplayName("um host que piora de score muda de bairro -- e isso ve-se")
    void movesToAnotherDistrictWhenItsRiskChanges() {
        HostPosition before = layoutOf(host("192.168.1.50", 30)).positionOf("192.168.1.50");
        HostPosition after = layoutOf(host("192.168.1.50", 90)).positionOf("192.168.1.50");

        assertThat(before.band()).isEqualTo(RiskBand.MEDIUM);
        assertThat(after.band()).isEqualTo(RiskBand.CRITICAL);
        assertThat(after.x()).isNotEqualTo(before.x());
    }

    @Test
    @DisplayName("as linhas sao aparadas: o host mais acima do bairro fica em z=0")
    void trimsEmptyRowsAtTheTopOfEachDistrict() {
        // .200 e .210 estao nas linhas 12 e 13 de um /24; sem aparar, z seria 48 e 52.
        CityLayout city = layoutOf(host("192.168.1.200", 90), host("192.168.1.210", 90));

        assertThat(city.positionOf("192.168.1.200").z()).isZero();
        assertThat(city.positionOf("192.168.1.210").z()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("cada bairro e aparado por si -- um nao empurra o outro")
    void trimsEachDistrictIndependently() {
        CityLayout city = layoutOf(host("192.168.1.200", 90), host("192.168.1.5", 10));

        assertThat(city.positionOf("192.168.1.200").z()).isZero();
        assertThat(city.positionOf("192.168.1.5").z()).isZero();
    }

    @Test
    @DisplayName("um bairro vazio mantem o seu espaco -- os outros nao deslizam")
    void keepsEmptyDistrictsInPlace() {
        CityLayout onlyLow = layoutOf(host("192.168.1.5", 10));

        assertThat(onlyLow.districts()).extracting(District::band)
                .containsExactly(RiskBand.CRITICAL, RiskBand.HIGH, RiskBand.MEDIUM,
                        RiskBand.LOW, RiskBand.UNKNOWN);
        assertThat(onlyLow.districts().get(0).hostCount()).isZero();
        assertThat(onlyLow.districts().get(0).depth()).isZero();
        // O bairro LOW esta onde estaria se houvesse hosts em todos os outros.
        assertThat(onlyLow.positionOf("192.168.1.5").x())
                .isEqualTo(districtX(RiskBand.LOW) + 5 * 4.0);
    }

    @Test
    @DisplayName("num /16 o indice e relativo a rede: .1.5 e .2.5 nao colidem")
    void doesNotCollideOnLargerNetworks() {
        CityLayout city = calculator.calculate("192.168.0.0/16",
                List.of(host("192.168.1.5", 90), host("192.168.2.5", 90)), List.of());

        assertThat(city.positionOf("192.168.1.5"))
                .isNotEqualTo(city.positionOf("192.168.2.5"));
    }

    @Test
    @DisplayName("um /24 cheio ocupa 254 celulas distintas")
    void neverPutsTwoHostsInTheSameCell() {
        List<Host> hosts = IntStream.rangeClosed(1, 254)
                .mapToObj(octet -> host("192.168.1." + octet, octet % 101))
                .toList();

        CityLayout city = calculator.calculate(TARGET, hosts, List.of());

        assertThat(city.positions()).hasSize(254);
        assertThat(city.positions().values().stream()
                .map(position -> position.x() + ":" + position.z()).distinct().count())
                .isEqualTo(254);
    }

    @Test
    @DisplayName("um host sem score vai para o bairro UNKNOWN, nao para o de risco baixo")
    void putsUnscoredHostsInTheirOwnDistrict() {
        assertThat(layoutOf(host("192.168.1.9", null)).positionOf("192.168.1.9").band())
                .isEqualTo(RiskBand.UNKNOWN);
    }

    @Test
    @DisplayName("IPv6 ou endereco fora da rede vao para o overflow, sem colidir nem rebentar")
    void placesAddressesOutsideTheNetworkInAnOverflowArea() {
        CityLayout city = calculator.calculate(TARGET, List.of(
                host("fe80::1", 90), host("10.0.0.7", 90), host("192.168.1.5", 90)), List.of());

        assertThat(city.positions()).hasSize(3);
        assertThat(city.positions().values().stream()
                .map(position -> position.x() + ":" + position.z()).distinct()).hasSize(3);
    }

    @Test
    @DisplayName("as ruinas entram no mesmo mapa, na faixa que tinham no baseline")
    void givesDisappearedHostsAPositionToo() {
        CityLayout city = calculator.calculate(TARGET,
                List.of(host("192.168.1.1", 90)),
                List.of(host("192.168.1.42", 10)));

        assertThat(city.positionOf("192.168.1.42")).isNotNull();
        assertThat(city.positionOf("192.168.1.42").band()).isEqualTo(RiskBand.LOW);
        assertThat(city.districts().get(RiskBand.LOW.ordinal()).hostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("as dimensoes cobrem tudo o que foi colocado")
    void reportsBoundsThatContainEveryHost() {
        CityLayout city = layoutOf(host("192.168.1.254", 100), host("192.168.1.5", 10));

        assertThat(city.positions().values()).allSatisfy(position -> {
            assertThat(position.x()).isBetween(0.0, city.width());
            assertThat(position.z()).isBetween(0.0, city.depth());
        });
        assertThat(city.spacing()).isEqualTo(4.0);
    }

    @Test
    void handlesAnEmptyScan() {
        CityLayout city = calculator.calculate(TARGET, List.of(), List.of());

        assertThat(city.positions()).isEmpty();
        assertThat(city.depth()).isZero();
        assertThat(city.districts()).hasSize(RiskBand.values().length);
    }
}
