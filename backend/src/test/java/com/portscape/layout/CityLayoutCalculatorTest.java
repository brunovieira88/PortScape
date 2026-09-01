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

    /**
     * O bairro de uma faixa <b>nesta</b> cidade. Nao ha formula fechada: as faixas sem
     * hosts sao saltadas e cada bairro ocupa so a largura de que precisa, por isso o x
     * depende da composicao do scan.
     */
    private static District districtOf(CityLayout city, RiskBand band) {
        return city.districts().stream()
                .filter(district -> district.band() == band)
                .findFirst()
                .orElseThrow(() -> new AssertionError("sem bairro para a faixa " + band));
    }

    @Test
    @DisplayName("a faixa da o bairro e o IP da o lugar dentro dele")
    void placesAHostInItsBandDistrictAtItsOwnCell() {
        CityLayout city = layoutOf(host("192.168.1.254", 100), host("192.168.1.1", 100));
        HostPosition position = city.positionOf("192.168.1.254");

        assertThat(position.band()).isEqualTo(RiskBand.CRITICAL);
        // 254 % 16 = 14 e 1 % 16 = 1: duas colunas ocupadas, o .254 fica na segunda.
        assertThat(position.x()).isEqualTo(districtOf(city, RiskBand.CRITICAL).x() + 4.0);
    }

    @Test
    @DisplayName("dois scans com a mesma composicao dao exatamente as mesmas posicoes")
    void isStableAcrossScans() {
        Host[] hosts = {host("192.168.1.1", 90), host("192.168.1.73", 60), host("192.168.1.104", 10)};

        assertThat(layoutOf(hosts).positions()).isEqualTo(layoutOf(hosts).positions());
    }

    @Test
    @DisplayName("a ordem dos hosts pela grelha e sempre a mesma, seja qual for a ordem da lista")
    void doesNotDependOnTheOrderOfTheInputList() {
        Host a = host("192.168.1.1", 90);
        Host b = host("192.168.1.130", 90);
        Host c = host("192.168.1.40", 90);

        CityLayout one = calculator.calculate(TARGET, List.of(a, b, c), List.of());
        CityLayout other = calculator.calculate(TARGET, List.of(c, a, b), List.of());

        assertThat(one.positions()).isEqualTo(other.positions());
    }

    @Test
    @DisplayName("o bairro le-se por ordem de IP, da esquerda para a direita")
    void fillsTheDistrictInIpOrder() {
        CityLayout city = layoutOf(host("192.168.1.130", 90), host("192.168.1.1", 90),
                host("192.168.1.40", 90));

        // Tres hosts dao um bloco de 2 colunas: .1 e .40 na primeira linha, .130 abaixo.
        double districtX = districtOf(city, RiskBand.CRITICAL).x();
        assertThat(city.positionOf("192.168.1.1")).extracting(HostPosition::x, HostPosition::z)
                .containsExactly(districtX, 0.0);
        assertThat(city.positionOf("192.168.1.40")).extracting(HostPosition::x, HostPosition::z)
                .containsExactly(districtX + 4.0, 0.0);
        assertThat(city.positionOf("192.168.1.130")).extracting(HostPosition::x, HostPosition::z)
                .containsExactly(districtX, 4.0);
    }

    @Test
    @DisplayName("o bairro e um bloco cheio -- sem celulas vazias a nao ser na ultima linha")
    void packsEachDistrictWithoutHoles() {
        // Oito hosts espalhados por um /24 davam antes um bairro 6x5 com 27% de ocupacao.
        List<Host> hosts = List.of(host("192.168.1.1", 90), host("192.168.1.2", 90),
                host("192.168.1.5", 90), host("192.168.1.10", 90), host("192.168.1.20", 90),
                host("192.168.1.42", 90), host("192.168.1.100", 90), host("192.168.1.254", 90));

        CityLayout city = calculator.calculate(TARGET, hosts, List.of());
        District district = districtOf(city, RiskBand.CRITICAL);

        // ceil(sqrt(8)) = 3 colunas, 3 linhas: 8 hosts em 9 lugares.
        assertThat(district.width()).isEqualTo(3 * 4.0);
        assertThat(district.depth()).isEqualTo(3 * 4.0);
        // So a ultima linha pode ter buracos: as celulas vazias sao menos que uma linha.
        int columns = (int) Math.round(district.width() / 4.0);
        int rows = (int) Math.round(district.depth() / 4.0);
        assertThat(columns * rows - hosts.size()).isLessThan(columns);
    }

    @Test
    @DisplayName("um scan com poucos hosts da uma cidade pequena, nao uma grelha vazia")
    void keepsTheCityTightWhenThereAreFewHosts() {
        // Quatro hosts espalhados por um /24: sem compactar davam 60x52 unidades de
        // cidade quase toda vazia.
        CityLayout city = layoutOf(host("192.168.1.1", 90), host("192.168.1.40", 90),
                host("192.168.1.130", 90), host("192.168.1.200", 90));

        // ceil(sqrt(4)) = 2: um bloco 2x2 cheio, nao uma grelha de 254 lugares.
        assertThat(city.width()).isEqualTo(2 * 4.0);
        assertThat(city.depth()).isEqualTo(2 * 4.0);
    }

    @Test
    @DisplayName("uma rede cheia nao e compactada -- nao ha nada para encostar")
    void leavesADenseNetworkUntouched() {
        List<Host> hosts = IntStream.rangeClosed(1, 254)
                .mapToObj(octet -> host("192.168.1." + octet, 90))
                .toList();

        CityLayout city = calculator.calculate(TARGET, hosts, List.of());

        // Todas as 16 colunas e as 16 linhas estao ocupadas: a compactacao e um no-op.
        assertThat(districtOf(city, RiskBand.CRITICAL).width()).isEqualTo(16 * 4.0);
        assertThat(districtOf(city, RiskBand.CRITICAL).depth()).isEqualTo(16 * 4.0);
    }

    @Test
    @DisplayName("um bairro ocupa a largura que precisa, e o seguinte encosta-se a ele")
    void sizesEachDistrictToItsOwnContent() {
        CityLayout city = layoutOf(host("192.168.1.1", 90), host("192.168.1.5", 10));

        District critical = districtOf(city, RiskBand.CRITICAL);
        District low = districtOf(city, RiskBand.LOW);

        assertThat(critical.x()).isZero();
        assertThat(critical.width()).isEqualTo(4.0);
        // Um bairro de uma coluna mais as 4 colunas de intervalo.
        assertThat(low.x()).isEqualTo(4.0 + 4 * 4.0);
    }

    @Test
    @DisplayName("um host que piora de score muda de bairro -- e isso ve-se")
    void movesToAnotherDistrictWhenItsRiskChanges() {
        // A maquina critica fixa garante que o bairro vermelho existe nos dois cenarios:
        // o que se quer ver e o .50 a mudar de bairro, nao a cidade a compactar-se.
        Host anchor = host("192.168.1.1", 90);
        HostPosition before = calculator
                .calculate(TARGET, List.of(anchor, host("192.168.1.50", 30)), List.of())
                .positionOf("192.168.1.50");
        HostPosition after = calculator
                .calculate(TARGET, List.of(anchor, host("192.168.1.50", 90)), List.of())
                .positionOf("192.168.1.50");

        assertThat(before.band()).isEqualTo(RiskBand.MEDIUM);
        assertThat(after.band()).isEqualTo(RiskBand.CRITICAL);
        assertThat(after.x()).isNotEqualTo(before.x());
    }

    @Test
    @DisplayName("o bairro comeca sempre no topo -- nao ha linhas vazias por cima")
    void pullsEveryDistrictUpToTheTop() {
        // .200 e .210 estao nas linhas 12 e 13 de um /24; sem compactar, z seria 48 e 52.
        CityLayout city = layoutOf(host("192.168.1.200", 90), host("192.168.1.210", 90));

        assertThat(city.positions().values()).allSatisfy(position ->
                assertThat(position.z()).isZero());
        // Dois hosts dao um bloco de 2x1: ficam lado a lado, nao empilhados.
        assertThat(districtOf(city, RiskBand.CRITICAL).depth()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("cada bairro e compactado por si -- um nao empurra o outro")
    void compactsEachDistrictIndependently() {
        CityLayout city = layoutOf(host("192.168.1.200", 90), host("192.168.1.5", 10));

        assertThat(city.positionOf("192.168.1.200").z()).isZero();
        assertThat(city.positionOf("192.168.1.5").z()).isZero();
    }

    @Test
    @DisplayName("faixas sem hosts nao ganham bairro -- a cidade fica densa, nao um deserto")
    void dropsEmptyDistrictsSoTheCityStaysDense() {
        CityLayout onlyLow = layoutOf(host("192.168.1.5", 10));

        assertThat(onlyLow.districts()).extracting(District::band).containsExactly(RiskBand.LOW);
        // Sem faixas povoadas a esquerda e sem colunas vazias, o host encosta a origem.
        assertThat(onlyLow.positionOf("192.168.1.5").x()).isZero();
    }

    @Test
    @DisplayName("os bairros que sobram mantem a ordem das faixas, da mais grave para a menos")
    void keepsBandOrderWhenCompacting() {
        CityLayout city = layoutOf(host("192.168.1.1", 90), host("192.168.1.5", 10));

        assertThat(city.districts()).extracting(District::band)
                .containsExactly(RiskBand.CRITICAL, RiskBand.LOW);
        assertThat(districtOf(city, RiskBand.CRITICAL).x())
                .isLessThan(districtOf(city, RiskBand.LOW).x());
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
    @DisplayName("num /16 esparso a cidade nao fica com quilometros de vazio pelo meio")
    void staysSmallOnAWideButSparseNetwork() {
        // Sem compactar, .1.5 e .200.5 ficavam a 3184 linhas de distancia.
        CityLayout city = calculator.calculate("192.168.0.0/16",
                List.of(host("192.168.1.5", 90), host("192.168.200.5", 90)), List.of());

        assertThat(city.depth()).isEqualTo(4.0);
        assertThat(city.width()).isEqualTo(2 * 4.0);
    }

    @Test
    @DisplayName("um target nao normalizado nao manda hosts para o overflow")
    void masksTheTargetToItsNetworkAddress() {
        CityLayout city = calculator.calculate("192.168.1.5/24",
                List.of(host("192.168.1.1", 90), host("192.168.1.9", 90)), List.of());

        // Sem mascarar, o .1 caia abaixo da rede e ia parar a zona de overflow.
        assertThat(districtOf(city, RiskBand.CRITICAL).width()).isEqualTo(2 * 4.0);
        assertThat(city.positionOf("192.168.1.1").x())
                .isLessThan(city.positionOf("192.168.1.9").x());
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
        assertThat(districtOf(city, RiskBand.LOW).hostCount()).isEqualTo(1);
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
    @DisplayName("cada bairro cobre os seus proprios hosts, para a placa de chao assentar")
    void reportsDistrictBoundsThatContainTheirOwnHosts() {
        CityLayout city = layoutOf(host("192.168.1.1", 90), host("192.168.1.40", 90),
                host("192.168.1.5", 10));

        for (District district : city.districts()) {
            assertThat(city.positions().values())
                    .filteredOn(position -> position.band() == district.band())
                    .allSatisfy(position -> {
                        assertThat(position.x()).isBetween(district.x(),
                                district.x() + district.width() - city.spacing());
                        assertThat(position.z()).isBetween(0.0, district.depth() - city.spacing());
                    });
        }
    }

    @Test
    void handlesAnEmptyScan() {
        CityLayout city = calculator.calculate(TARGET, List.of(), List.of());

        assertThat(city.positions()).isEmpty();
        assertThat(city.depth()).isZero();
        assertThat(city.width()).isZero();
        assertThat(city.districts()).isEmpty();
    }
}
