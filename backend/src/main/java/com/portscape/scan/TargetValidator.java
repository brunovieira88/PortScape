package com.portscape.scan;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.portscape.scan.exception.InvalidTargetException;

/**
 * Porta de entrada de tudo o que vai parar a linha de comandos do nmap.
 *
 * <p>Faz duas coisas distintas:
 * <ol>
 *   <li><b>Sintaxe.</b> So aceita IPv4 ou IPv4/CIDR. O {@code ProcessBuilder} nao
 *       usa shell, portanto nao ha injecao de shell, mas ha <i>injecao de
 *       argumentos</i>: sem esta barreira um "target" como {@code --script=vuln}
 *       ou {@code -oN /etc/algo} seria passado ao nmap como flag.</li>
 *   <li><b>Etica.</b> So aceita redes privadas (RFC1918) e loopback. Esta e a
 *       restricao rigida do projeto: o Portscape nunca faz scan de IPs publicos.
 *       Fica codificada aqui, nao apenas documentada no README.</li>
 * </ol>
 */
@Component
public class TargetValidator {

    private static final Pattern IPV4_CIDR =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:/(\\d{1,2}))?$");

    /**
     * Prefixo minimo aceite. Alem de evitar scans absurdamente largos, garante a
     * verificacao de privacidade abaixo: todos os blocos privados tem prefixo <= 16,
     * logo qualquer rede /16 ou mais estreita cuja base caia num bloco privado esta
     * inteiramente contida nesse bloco.
     */
    private static final int MIN_PREFIX = 16;
    private static final int MAX_PREFIX = 32;

    /**
     * Valida o target e devolve-o normalizado (endereco de rede + prefixo), para
     * que {@code 192.168.1.7/24} e {@code 192.168.1.0/24} sejam o mesmo target --
     * o que a fase 2 vai precisar para comparar scans da mesma subnet.
     *
     * @throws InvalidTargetException se o formato for invalido ou a rede nao for privada
     */
    public String validate(String target) {
        if (target == null || target.isBlank()) {
            throw new InvalidTargetException("Target nao pode estar vazio");
        }
        String trimmed = target.trim();

        var matcher = IPV4_CIDR.matcher(trimmed);
        if (!matcher.matches()) {
            throw new InvalidTargetException(
                    "Target invalido: '" + trimmed + "'. Formato esperado: IPv4 ou IPv4/CIDR, ex. 192.168.1.0/24");
        }

        int address = 0;
        for (int group = 1; group <= 4; group++) {
            int octet = Integer.parseInt(matcher.group(group));
            if (octet > 255) {
                throw new InvalidTargetException(
                        "Target invalido: '" + trimmed + "'. Octeto fora do intervalo 0-255: " + octet);
            }
            address = (address << 8) | octet;
        }

        int prefix = matcher.group(5) == null ? MAX_PREFIX : Integer.parseInt(matcher.group(5));
        if (prefix < MIN_PREFIX || prefix > MAX_PREFIX) {
            throw new InvalidTargetException(
                    "Prefixo invalido: /" + prefix + ". Permitido /" + MIN_PREFIX + " a /" + MAX_PREFIX);
        }

        int network = address & maskOf(prefix);
        if (!isPrivate(network)) {
            throw new InvalidTargetException(
                    "Target recusado: '" + trimmed + "' nao pertence a uma rede privada. "
                            + "O Portscape so faz scan de 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 ou 127.0.0.0/8.");
        }

        return format(network) + (prefix == MAX_PREFIX ? "" : "/" + prefix);
    }

    private static int maskOf(int prefix) {
        // Deslocar 32 bits em int e no-op em Java, dai o caso especial.
        return prefix == 0 ? 0 : -1 << (32 - prefix);
    }

    private static boolean isPrivate(int address) {
        return matches(address, 10, 0, 0, 0, 8)         // 10.0.0.0/8
                || matches(address, 172, 16, 0, 0, 12)  // 172.16.0.0/12
                || matches(address, 192, 168, 0, 0, 16) // 192.168.0.0/16
                || matches(address, 127, 0, 0, 0, 8);   // loopback
    }

    private static boolean matches(int address, int a, int b, int c, int d, int prefix) {
        int block = (a << 24) | (b << 16) | (c << 8) | d;
        int mask = maskOf(prefix);
        return (address & mask) == block;
    }

    private static String format(int address) {
        return "%d.%d.%d.%d".formatted(
                (address >>> 24) & 0xFF,
                (address >>> 16) & 0xFF,
                (address >>> 8) & 0xFF,
                address & 0xFF);
    }
}
