package com.portscape.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.portscape.config.LayoutProperties;
import com.portscape.config.RiskProperties;
import com.portscape.domain.Host;
import com.portscape.risk.RiskBand;

/**
 * Calcula onde cada edificio fica na cidade.
 *
 * <p><b>A faixa de risco escolhe o bairro; o IP escolhe o lugar dentro dele.</b> Ver
 * uma maquina migrar para o bairro vermelho e informacao, nao ruido.
 *
 * <p><b>Cada bairro e um bloco denso.</b> Uma rede /24 tem 254 lugares e um scan
 * tipico enche cinco. Desenhar a grelha toda dava uma cidade que e quase so alcatrao,
 * com os edificios longe demais uns dos outros para se lerem como um conjunto. Por
 * isso o bairro nao herda a forma que o IP daria a grelha: os hosts sao ordenados por
 * IP e preenchem um bloco o mais quadrado possivel, {@code ceil(sqrt(n))} colunas de
 * largura. Cinco hosts dao um 3x2 cheio, nao um 5x5 com quatro quintos de vazio.
 *
 * <p>Isto <b>coincide</b> com a grelha antiga quando o bairro esta cheio: 254 hosts
 * dao {@code ceil(sqrt(254))} = 16 colunas por 16 linhas, exatamente a malha 16x16 que
 * um /24 sempre teve. Nao e uma alternativa a grelha, e uma generalizacao dela que so
 * se afasta no caso esparso -- que e o caso real.
 *
 * <p>O que isto <b>preserva</b> e a ordem: os hosts leem-se por ordem de IP da
 * esquerda para a direita e de cima para baixo, como um texto. O que isto <b>custa</b>
 * e a coordenada absoluta e a leitura por sub-bloco de rede -- a linha deixou de ser o
 * {@code /28} a que o host pertence. E o mesmo compromisso ja assumido para os bairros
 * vazios, que tambem sao saltados: a posicao <i>relativa</i> e que e estavel.
 *
 * <p>A alternativa -- compactar no frontend, ao arredondar as coordenadas para uma
 * grelha mais apertada -- parece equivalente e nao e: colapsa varios hosts na mesma
 * celula e obriga a desempatar por varrimento, o que faz a posicao de um host depender
 * de quais os outros hosts do scan e da ordem por que foram processados. Feita aqui, a
 * compactacao e injetiva (dois hosts nunca partilham celula), determinista e testavel.
 *
 * <p>Puro e determinista de proposito -- sem estado, sem BD, sem relogio. E a parte
 * do projeto mais facil de testar isoladamente.
 */
@Component
public class CityLayoutCalculator {

    private final LayoutProperties layout;
    private final RiskProperties risk;

    public CityLayoutCalculator(LayoutProperties layout, RiskProperties risk) {
        this.layout = layout;
        this.risk = risk;
    }

    /**
     * @param target rede do scan em CIDR, ex. {@code 192.168.1.0/24}
     * @param hosts  hosts que responderam
     * @param ruins  hosts que existiam no baseline e ja nao respondem -- entram no
     *               mesmo mapa para poderem ser desenhados no lugar onde estavam
     */
    public CityLayout calculate(String target, List<Host> hosts, List<Host> ruins) {
        List<Host> all = new ArrayList<>(hosts);
        all.addAll(ruins);

        Map<String, Long> indexByIp = assignIndexes(target, all);
        Map<RiskBand, List<Host>> byBand = groupByBand(all);

        Map<String, HostPosition> positions = new LinkedHashMap<>();
        List<District> districts = new ArrayList<>();

        double nextDistrictX = 0;

        for (RiskBand band : RiskBand.values()) {
            List<Host> members = byBand.getOrDefault(band, List.of());
            // Uma faixa sem hosts nao ganha bairro nenhum. Reservar-lhe o espaco
            // deixava buracos do tamanho de um bairro inteiro entre zonas habitadas,
            // e uma cidade com mais vazio do que edificios nao se le nem se percorre.
            if (members.isEmpty()) {
                continue;
            }

            // Por ordem de IP: e essa a ordem por que o bairro se le.
            List<Host> ordered = members.stream()
                    .sorted(Comparator.comparingLong(host -> indexByIp.get(host.ip())))
                    .toList();

            int columns = columnsFor(ordered.size());
            int rows = (ordered.size() + columns - 1) / columns;

            double districtX = nextDistrictX;
            double districtWidth = columns * layout.spacing();
            double districtDepth = rows * layout.spacing();
            // O bairro seguinte comeca a seguir a este, nao numa grelha fixa: um bairro
            // com tres colunas ocupa tres colunas.
            nextDistrictX += districtWidth + layout.districtGap() * layout.spacing();

            for (int i = 0; i < ordered.size(); i++) {
                Host host = ordered.get(i);
                positions.put(host.ip(), new HostPosition(host.ip(), band,
                        districtX + (i % columns) * layout.spacing(),
                        (i / columns) * layout.spacing()));
            }

            districts.add(new District(band, districtX, districtWidth, districtDepth, ordered.size()));
        }

        District last = districts.isEmpty() ? null : districts.get(districts.size() - 1);
        double width = last == null ? 0 : last.x() + last.width();
        double depth = districts.stream().mapToDouble(District::depth).max().orElse(0);
        return new CityLayout(positions, districts, layout.spacing(), width, depth);
    }

    /**
     * Largura do bloco de um bairro, em colunas: o lado de um quadrado que caiba os
     * hosts todos, limitado por {@code grid-width} para um bairro muito povoado nao se
     * esticar ate a cidade deixar de se ver de uma vez.
     */
    private int columnsFor(int hostCount) {
        return Math.min(layout.gridWidth(), (int) Math.ceil(Math.sqrt(hostCount)));
    }

    private Map<RiskBand, List<Host>> groupByBand(List<Host> hosts) {
        Map<RiskBand, List<Host>> byBand = new EnumMap<>(RiskBand.class);
        for (Host host : hosts) {
            byBand.computeIfAbsent(RiskBand.of(host.risk(), risk), band -> new ArrayList<>())
                    .add(host);
        }
        return byBand;
    }

    /**
     * Indice de cada host dentro da rede: {@code ip - enderecoDeRede}. Num /24 isto e
     * exatamente o ultimo octeto; num /16 e o que impede {@code 192.168.1.5} e
     * {@code 192.168.2.5} de irem parar a mesma celula, como aconteceria se se usasse
     * so o ultimo octeto.
     *
     * <p>Um host que nao caiba nesta conta -- IPv6, ou um endereco fora da rede do
     * target -- recebe um indice acima de todos os outros, por ordem de IP. Como o
     * bairro e preenchido por ordem de indice, isso poe-no no fim do seu bairro (canto
     * inferior direito) e nao numa zona a parte: depois da compactacao ele fica
     * encostado aos restantes, sem folga visivel entre eles. Nao devia acontecer;
     * rebentar a cena por causa disso seria pior do que arruma-lo num sitio
     * previsivel.
     */
    private Map<String, Long> assignIndexes(String target, List<Host> hosts) {
        long network = networkAddressOf(target);
        long size = networkSizeOf(target);

        Map<String, Long> indexes = new LinkedHashMap<>();
        List<String> overflow = new ArrayList<>();
        for (Host host : hosts) {
            Optional<Long> address = ipv4ToLong(host.ip());
            if (address.isPresent() && address.get() >= network && address.get() < network + size) {
                indexes.put(host.ip(), address.get() - network);
            } else {
                overflow.add(host.ip());
            }
        }

        overflow.sort(Comparator.naturalOrder());
        long next = size;
        for (String ip : overflow) {
            indexes.put(ip, next++);
        }
        return indexes;
    }

    /**
     * Endereco de rede do target. O {@link com.portscape.scan.TargetValidator} ja
     * normaliza o que entra pela API, mas mascarar aqui tambem mantem a classe correta
     * por si so -- sem isto, um {@code 192.168.1.5/24} vindo de um teste ou de outro
     * chamador mandava os hosts .1 a .4 para o overflow.
     */
    private static long networkAddressOf(String target) {
        long address = ipv4ToLong(addressPart(target)).orElse(0L);
        long size = networkSizeOf(target);
        return address - Math.floorMod(address, size);
    }

    private static long networkSizeOf(String target) {
        int slash = target.indexOf('/');
        if (slash < 0) {
            return 1L;
        }
        try {
            int prefix = Integer.parseInt(target.substring(slash + 1));
            return 1L << (32 - Math.clamp(prefix, 0, 32));
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    private static String addressPart(String target) {
        int slash = target.indexOf('/');
        return slash < 0 ? target : target.substring(0, slash);
    }

    /** Converte um IPv4 literal num inteiro. Nao resolve nomes -- so aceita digitos. */
    private static Optional<Long> ipv4ToLong(String ip) {
        if (ip == null) {
            return Optional.empty();
        }
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) {
            return Optional.empty();
        }
        long value = 0;
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) {
                return Optional.empty();
            }
            for (int i = 0; i < octet.length(); i++) {
                if (!Character.isDigit(octet.charAt(i))) {
                    return Optional.empty();
                }
            }
            int parsed = Integer.parseInt(octet);
            if (parsed > 255) {
                return Optional.empty();
            }
            value = (value << 8) | parsed;
        }
        return Optional.of(value);
    }
}
