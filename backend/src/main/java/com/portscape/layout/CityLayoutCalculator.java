package com.portscape.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.LongUnaryOperator;

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
 * <p><b>A cidade e compactada, e a compactacao e feita aqui.</b> Uma rede /24 tem 254
 * lugares e um scan tipico enche cinco. Desenhar a grelha toda dava uma cidade que e
 * quase so alcatrao, com os edificios longe demais uns dos outros para se lerem como
 * um conjunto. Por isso, dentro de cada bairro, as colunas e as linhas <i>ocupadas</i>
 * sao numeradas por ordem e encostadas: quatro colunas ocupadas ficam nas posicoes 0 a
 * 3, independentemente de terem saido dos IPs .1, .40, .130 e .200. O mesmo para as
 * linhas, e o mesmo para os bairros entre si -- cada um ocupa a largura de que precisa,
 * nao uma fatia fixa.
 *
 * <p>O que isto <b>preserva</b> e a ordem: dois hosts na mesma coluna continuam na
 * mesma coluna, e um host a esquerda de outro continua a esquerda dele. O que isto
 * <b>custa</b> e a coordenada absoluta -- o .254 nao esta sempre na coluna 14, esta na
 * ultima coluna ocupada do seu bairro. E o mesmo compromisso ja assumido para os
 * bairros vazios, que tambem sao saltados: a posicao <i>relativa</i> e que e estavel.
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

            // Coluna e linha vem do indice do host; o rank denso encosta as ocupadas.
            Map<Long, Integer> columns = denseRank(members, indexByIp, this::columnOf);
            Map<Long, Integer> rows = denseRank(members, indexByIp, this::rowOf);

            double districtX = nextDistrictX;
            double districtWidth = columns.size() * layout.spacing();
            double districtDepth = rows.size() * layout.spacing();
            // O bairro seguinte comeca a seguir a este, nao numa grelha fixa: um bairro
            // com tres colunas ocupa tres colunas.
            nextDistrictX += districtWidth + layout.districtGap() * layout.spacing();

            for (Host host : members) {
                long index = indexByIp.get(host.ip());
                positions.put(host.ip(), new HostPosition(host.ip(), band,
                        districtX + columns.get(columnOf(index)) * layout.spacing(),
                        rows.get(rowOf(index)) * layout.spacing()));
            }

            districts.add(new District(band, districtX, districtWidth, districtDepth, members.size()));
        }

        District last = districts.isEmpty() ? null : districts.get(districts.size() - 1);
        double width = last == null ? 0 : last.x() + last.width();
        double depth = districts.stream().mapToDouble(District::depth).max().orElse(0);
        return new CityLayout(positions, districts, layout.spacing(), width, depth);
    }

    private long columnOf(long index) {
        return index % layout.gridWidth();
    }

    private long rowOf(long index) {
        return index / layout.gridWidth();
    }

    /**
     * Numera por ordem os valores <i>usados</i> de uma coordenada: {@code {1, 40, 130}}
     * da {@code {1->0, 40->1, 130->2}}. E o que encosta os edificios sem os reordenar.
     *
     * <p>Aplicar isto as colunas e as linhas de forma independente continua a nao
     * gerar colisoes: os pares (coluna, linha) sao unicos a partida -- vem do indice
     * do host, que e unico -- e numerar cada eixo separadamente e injetivo em cada
     * eixo, logo o par continua unico.
     */
    private static Map<Long, Integer> denseRank(List<Host> members, Map<String, Long> indexByIp,
            LongUnaryOperator coordinate) {
        Map<Long, Integer> ranks = new TreeMap<>();
        for (Host host : members) {
            ranks.put(coordinate.applyAsLong(indexByIp.get(host.ip())), 0);
        }
        int rank = 0;
        for (Map.Entry<Long, Integer> entry : ranks.entrySet()) {
            entry.setValue(rank++);
        }
        return ranks;
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
     * target -- vai para uma zona de overflow no fim da grelha, com os lugares
     * atribuidos por ordem de IP. Nao devia acontecer; rebentar a cena por causa
     * disso seria pior do que arruma-lo algures de forma previsivel.
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
