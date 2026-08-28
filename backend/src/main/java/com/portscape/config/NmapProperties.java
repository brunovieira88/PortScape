package com.portscape.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do nmap. Nada disto pode estar hardcoded no codigo: o caminho do
 * binario, os argumentos e a subnet por defeito mudam de maquina para maquina.
 *
 * @param command       prefixo do comando, ex. ["/opt/homebrew/bin/nmap"] ou
 *                      ["sudo", "-n", "/opt/homebrew/bin/nmap"] para scan privilegiado
 * @param defaultTarget rede de seguranca: so e usada quando o pedido nao
 *                      especifica target E a deteccao automatica da subnet local
 *                      ({@link com.portscape.scan.LocalNetworkDetector}) falha
 * @param arguments     flags da fase de descoberta -- portas e OS, NAO -sV (ver
 *                      {@link com.portscape.scan.NmapCommandBuilder}); o -oX - e o
 *                      --host-timeout sao acrescentados pelo builder
 * @param timeout       tempo maximo do processo antes de ser morto
 * @param hostTimeout   valor passado ao --host-timeout do nmap
 */
@ConfigurationProperties(prefix = "portscape.nmap")
public record NmapProperties(
        List<String> command,
        String defaultTarget,
        List<String> arguments,
        Duration timeout,
        Duration hostTimeout
) {
    public NmapProperties {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("portscape.nmap.command nao pode estar vazio");
        }
        command = List.copyOf(command);
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
        hostTimeout = hostTimeout == null ? Duration.ofSeconds(60) : hostTimeout;
    }

    /** Caminho do binario, para mensagens de erro e para o preflight. */
    public String binary() {
        return command.get(command.size() - 1);
    }
}
