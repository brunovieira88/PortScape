package com.portscape.scan;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Descobre a subnet local a partir da interface da rota por defeito -- a mesma que
 * o sistema operativo usaria para trafego normal.
 *
 * <p>Uma maquina pode ter varias interfaces ativas ao mesmo tempo (Wi-Fi e
 * Ethernet, VPN, adaptadores virtuais do Docker). Escolher "a primeira interface
 * privada que aparecer" e ambiguo -- foi exatamente esse tipo de ambiguidade que
 * fez o Docker escolher a rede errada (ver {@code --network host} no README).
 * Perguntar ao SO qual e a rota por defeito resolve a maioria dos casos, porque e
 * a mesma decisao que qualquer aplicacao normal (o browser, por exemplo) usaria.
 */
@Component
public class LocalNetworkDetector {

    private static final Logger log = LoggerFactory.getLogger(LocalNetworkDetector.class);

    /**
     * Endereco publico bem conhecido, usado so para o SO escolher a rota. Um
     * socket UDP "ligado" a este endereco nunca envia um pacote -- {@code connect}
     * num socket UDP so consulta a tabela de rotas do kernel para decidir que
     * interface e endereco local usaria.
     */
    private static final String ROUTE_PROBE_ADDRESS = "8.8.8.8";
    private static final int ROUTE_PROBE_PORT = 80;

    /**
     * @return a subnet da interface de rota por defeito, em notacao CIDR (ex.
     *         "192.168.1.0/24"), ou vazio se nao foi possivel determinar (sem rota
     *         por defeito, ambiente isolado, etc.) -- nesse caso o chamador deve
     *         cair para {@code portscape.nmap.default-target}.
     */
    public Optional<String> detectLocalSubnet() {
        try {
            InetAddress localAddress = localAddressForDefaultRoute();
            NetworkInterface iface = NetworkInterface.getByInetAddress(localAddress);
            if (iface == null) {
                log.warn("Nao encontrei a interface de rede para o endereco {}", localAddress.getHostAddress());
                return Optional.empty();
            }

            for (InterfaceAddress ifaceAddress : iface.getInterfaceAddresses()) {
                if (localAddress.equals(ifaceAddress.getAddress())) {
                    String cidr = toNetworkCidr(localAddress, ifaceAddress.getNetworkPrefixLength());
                    log.info("Subnet local detetada via '{}' ({}): {}", iface.getName(),
                            localAddress.getHostAddress(), cidr);
                    return Optional.of(cidr);
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Deteccao automatica da rede local falhou (sem rota por defeito?): {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static InetAddress localAddressForDefaultRoute() throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(ROUTE_PROBE_ADDRESS), ROUTE_PROBE_PORT);
            return socket.getLocalAddress();
        }
    }

    /** @param address garantido IPv4 -- resulta de um connect a um literal IPv4 */
    private static String toNetworkCidr(InetAddress address, short prefixLength) {
        byte[] bytes = address.getAddress();
        int addr = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        int mask = prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
        int network = addr & mask;
        return "%d.%d.%d.%d/%d".formatted(
                (network >>> 24) & 0xFF, (network >>> 16) & 0xFF, (network >>> 8) & 0xFF, network & 0xFF,
                prefixLength);
    }
}
