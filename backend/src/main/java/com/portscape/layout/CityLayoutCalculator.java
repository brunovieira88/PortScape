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
 * <p><b>A faixa de risco escolhe o bairro; o IP escolhe o lugar dentro dele.</b> A
 * coluna sai de {@code indiceDoHost % larguraDaGrelha} e por isso nunca muda: o
 * {@code .254} esta sempre na coluna 14, em todos os scans e em qualquer bairro. Um
 * host so se desloca quando muda de faixa de risco -- e numa auditoria ver uma
 * maquina migrar para o bairro vermelho e informacao, nao ruido.
 *
 * <p>As linhas sao <b>aparadas</b> por bairro (o host mais acima fica em {@code z=0}),
 * senao um /24 com seis maquinas seria um deserto de 254 lugares. O preco assumido:
 * a linha de um host pode deslizar se o bairro passar a ocupar uma zona diferente.
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

        for (RiskBand band : RiskBand.values()) {
            List<Host> members = byBand.getOrDefault(band, List.of());
            double districtX = band.ordinal() * layout.districtStride() * layout.spacing();
            long minRow = members.stream()
                    .mapToLong(host -> indexByIp.get(host.ip()) / layout.gridWidth())
                    .min().orElse(0L);
            long maxRow = members.stream()
                    .mapToLong(host -> indexByIp.get(host.ip()) / layout.gridWidth())
                    .max().orElse(-1L);

            for (Host host : members) {
                long index = indexByIp.get(host.ip());
                long column = index % layout.gridWidth();
                long row = index / layout.gridWidth() - minRow;
                positions.put(host.ip(), new HostPosition(host.ip(), band,
                        districtX + column * layout.spacing(),
                        row * layout.spacing()));
            }

            double depth = members.isEmpty() ? 0 : (maxRow - minRow + 1) * layout.spacing();
            districts.add(new District(band, districtX,
                    layout.gridWidth() * layout.spacing(), depth, members.size()));
        }

        double width = districts.isEmpty() ? 0
                : districts.get(districts.size() - 1).x() + layout.gridWidth() * layout.spacing();
        double depth = districts.stream().mapToDouble(District::depth).max().orElse(0);
        return new CityLayout(positions, districts, layout.spacing(), width, depth);
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

    private static long networkAddressOf(String target) {
        return ipv4ToLong(addressPart(target)).orElse(0L);
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
