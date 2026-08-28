package com.portscape.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.portscape.config.NmapProperties;

/**
 * Monta as linhas de comando do nmap. Separado do executor para poder ser testado
 * sem lancar processos.
 *
 * <p>O scan e feito em duas fases distintas (ver {@link ScanService}) por causa de
 * um bug do nmap em macOS: quando corre como root, o motor de deteccao de versao
 * ({@code -sV}) falha a vincular as suas sondas ({@code NSOCK ERROR
 * mksock_bind_addr ... Invalid argument}) e todas as portas saem como
 * {@code tcpwrapped}, independentemente de se usar {@code -sS}, {@code -sT} ou
 * {@code -O}. So acontece como root. A fase de descoberta corre privilegiada
 * (portas + OS); a deteccao de versao corre depois, sem privilegios, so contra os
 * hosts e portas que a primeira fase encontrou.
 */
@Component
public class NmapCommandBuilder {

    private final NmapProperties properties;

    public NmapCommandBuilder(NmapProperties properties) {
        this.properties = properties;
    }

    /**
     * Fase 1: descoberta de hosts, portas e OS. Usa {@code portscape.nmap.command}
     * (tipicamente privilegiado, via sudo) e {@code portscape.nmap.arguments}.
     *
     * @param target target ja validado por {@link TargetValidator} -- este metodo
     *               assume-o confiavel e nao volta a verificar
     */
    public List<String> buildDiscovery(String target) {
        List<String> command = new ArrayList<>(properties.command());
        command.addAll(properties.arguments());
        command.add("--host-timeout");
        command.add(properties.hostTimeout().toSeconds() + "s");
        // XML para stdout: evita ficheiros temporarios e a limpeza que trariam.
        command.add("-oX");
        command.add("-");
        // Target sempre em ultimo, como o nmap espera.
        command.add(target);
        return List.copyOf(command);
    }

    /**
     * Fase 2: deteccao de versao dos servicos. Corre sempre sem privilegios --
     * {@code -sT -sV} nao precisa de root, e correr como root e exatamente o que
     * despoleta o bug descrito na classe. Por isso usa o binario diretamente
     * ({@link NmapProperties#binary()}), ignorando um eventual prefixo {@code sudo}
     * em {@code portscape.nmap.command}.
     *
     * @param hostIps hosts a inquirir (tipicamente os que a fase 1 encontrou up)
     * @param ports   portas a verificar (tipicamente a uniao das portas abertas
     *                encontradas na fase 1); se vazio, o chamador deve saltar esta
     *                fase em vez de invocar isto -- sem {@code -p} o nmap cairia
     *                para o seu conjunto de portas por defeito, que pode nao bater
     *                certo com o que a fase 1 encontrou
     */
    public List<String> buildVersionDetection(List<String> hostIps, List<Integer> ports) {
        List<String> command = new ArrayList<>();
        command.add(properties.binary());
        command.add("-sT");
        command.add("-sV");
        command.add("--open");
        command.add("--host-timeout");
        command.add(properties.hostTimeout().toSeconds() + "s");
        command.add("-p");
        command.add(ports.stream().map(String::valueOf).collect(Collectors.joining(",")));
        command.add("-oX");
        command.add("-");
        command.addAll(hostIps);
        return List.copyOf(command);
    }
}
