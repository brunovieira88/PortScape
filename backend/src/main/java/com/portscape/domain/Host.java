package com.portscape.domain;

import java.util.List;

import com.portscape.risk.RiskScore;

/**
 * Um host que respondeu ao scan. {@code hostname} e {@code osGuess} podem ser null.
 *
 * <p>{@code risk} e null enquanto o scan corre e so e preenchido no fim, quando ja
 * ha CVEs e baseline com que pontuar.
 *
 * @param mac    endereco fisico, quando o nmap o conseguiu resolver. So aparece para
 *               maquinas no mesmo segmento de rede e com o scan privilegiado -- a
 *               propria maquina que corre o scan nunca traz MAC, nem um alvo de
 *               loopback. Ver {@link #identity()}
 * @param vendor fabricante que o nmap deduz do prefixo do MAC ("Apple, Inc.",
 *               "Espressif"). Diz <i>o que</i> e a maquina, coisa que o IP nao diz
 */
public record Host(
        String ip,
        String mac,
        String vendor,
        String hostname,
        String osGuess,
        Integer osAccuracy,
        List<Port> ports,
        RiskScore risk
) {
    public Host {
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    /** Host sem MAC -- o que sai de um scan nao privilegiado, ou de loopback. */
    public Host(String ip, String hostname, String osGuess, Integer osAccuracy,
            List<Port> ports, RiskScore risk) {
        this(ip, null, null, hostname, osGuess, osAccuracy, ports, risk);
    }

    /** Host ainda sem risco calculado -- e o que sai do parser do nmap. */
    public Host(String ip, String hostname, String osGuess, Integer osAccuracy, List<Port> ports) {
        this(ip, null, null, hostname, osGuess, osAccuracy, ports, null);
    }

    /**
     * O que identifica esta maquina entre scans: <b>o MAC quando existe, o IP quando nao</b>.
     *
     * <p>Um IP e um aluguer, nao uma identidade. Numa rede domestica com DHCP o mesmo
     * telemovel aparece como {@code .68} numa noite e {@code .70} na seguinte, e
     * comparar por IP fazia disso "um host desapareceu e nasceu outro" -- o que enche
     * de falsos alarmes justamente o sinal que este projecto existe para dar.
     *
     * <p>O recurso ao IP nao e um detalhe: ha casos legitimos sem MAC (o proprio
     * portatil que corre o scan, um alvo fora do segmento local, um scan sem
     * privilegios) e nesses o comportamento antigo continua a ser o melhor disponivel.
     */
    public String identity() {
        return mac == null || mac.isBlank() ? ip : mac;
    }

    public Host withRisk(RiskScore risk) {
        return new Host(ip, mac, vendor, hostname, osGuess, osAccuracy, ports, risk);
    }

    /** Altura do edificio na cena 3D deriva daqui (ver fase 4). */
    public int portCount() {
        return ports.size();
    }
}
