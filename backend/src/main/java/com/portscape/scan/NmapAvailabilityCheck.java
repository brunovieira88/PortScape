package com.portscape.scan;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.portscape.config.NmapProperties;
import com.portscape.scan.exception.ScanException;

/**
 * Corre {@code nmap --version} no arranque para o problema aparecer nos logs em vez
 * de so no primeiro scan.
 *
 * <p>Avisa, nao rebenta: a aplicacao continua util (a API responde, os erros ficam
 * registados nos jobs) e num container o nmap pode ainda nao estar montado.
 *
 * <p>Desligado no perfil "test": um teste de integracao nao tem nada que invocar o
 * nmap real (nem pedir sudo) so por levantar o contexto.
 */
@Component
@Profile("!test")
public class NmapAvailabilityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NmapAvailabilityCheck.class);

    private final NmapProperties properties;
    private final NmapExecutor executor;

    public NmapAvailabilityCheck(NmapProperties properties, NmapExecutor executor) {
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> command = new java.util.ArrayList<>(properties.command());
        command.add("--version");
        try {
            String version = executor.execute(command).lines().findFirst().orElse("(desconhecida)");
            log.info("nmap disponivel em '{}': {}", properties.binary(), version);
        } catch (ScanException e) {
            log.warn("nmap indisponivel em '{}' -- os scans vao falhar ate isto estar resolvido. Causa: {}",
                    properties.binary(), e.getMessage());
        }
    }
}
