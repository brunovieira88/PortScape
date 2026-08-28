package com.portscape.scan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.portscape.config.NmapProperties;
import com.portscape.scan.exception.NmapExecutionException;
import com.portscape.scan.exception.NmapNotFoundException;
import com.portscape.scan.exception.NmapPrivilegeException;

/**
 * Corre o nmap num processo externo e devolve o XML do stdout.
 *
 * <p>Dois detalhes que sao a origem habitual de bugs aqui:
 * <ul>
 *   <li>stdout e stderr sao drenados <b>em paralelo</b>. Juntar os dois
 *       ({@code redirectErrorStream(true)}) corromperia o XML, e ler um so de cada
 *       vez bloqueia assim que o outro pipe encher.</li>
 *   <li>o processo e morto a forca no timeout -- alem do {@code --host-timeout}
 *       que o proprio nmap respeita por host.</li>
 * </ul>
 */
@Component
public class NmapExecutor {

    private static final Logger log = LoggerFactory.getLogger(NmapExecutor.class);

    /** Quanto do stderr do nmap vai para a mensagem de erro. */
    private static final int STDERR_EXCERPT_LIMIT = 2000;

    private final NmapProperties properties;

    public NmapExecutor(NmapProperties properties) {
        this.properties = properties;
    }

    /**
     * @return o XML produzido pelo nmap
     * @throws NmapNotFoundException  binario inexistente ou nao executavel
     * @throws NmapPrivilegeException o scan pedido precisa de root e nao o tem
     * @throws NmapExecutionException qualquer outra falha, incluindo timeout
     */
    public String execute(List<String> command) {
        log.info("A executar: {}", String.join(" ", command));

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new NmapNotFoundException(
                    "Nao foi possivel executar o nmap em '" + properties.binary()
                            + "'. Confirma que esta instalado e que portscape.nmap.command esta correto.", e);
        }

        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());

        try {
            boolean finished = process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new NmapExecutionException(
                        "O scan excedeu o timeout de " + properties.timeout() + " e foi terminado.");
            }

            String output = stdout.get();
            String errors = stderr.get();
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                throw toFailure(exitCode, errors);
            }
            if (output.isBlank()) {
                throw new NmapExecutionException("O nmap terminou com sucesso mas nao produziu XML. stderr: "
                        + excerpt(errors));
            }
            if (!errors.isBlank()) {
                log.warn("nmap terminou com sucesso mas escreveu no stderr: {}", excerpt(errors));
            }
            return output;

        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new NmapExecutionException("O scan foi interrompido.", e);
        } catch (ExecutionException e) {
            process.destroyForcibly();
            throw new NmapExecutionException("Falha ao ler o output do nmap.", e.getCause());
        }
    }

    private NmapExecutionException toFailure(int exitCode, String stderr) {
        if (requiresRoot(stderr)) {
            throw new NmapPrivilegeException(privilegeHelp(stderr));
        }
        return new NmapExecutionException(
                "O nmap terminou com codigo " + exitCode + ". stderr: " + excerpt(stderr));
    }

    private static boolean requiresRoot(String stderr) {
        String lower = stderr.toLowerCase(Locale.ROOT);
        return lower.contains("requires root privileges")
                || lower.contains("requires r00t")
                || lower.contains("you must be root")
                || lower.contains("operation not permitted");
    }

    private String privilegeHelp(String stderr) {
        return """
                O scan pedido (-sS / -O) precisa de privilegios de root e o nmap recusou.
                Opcoes:
                  1. sudoers (recomendado): sudo visudo -f /etc/sudoers.d/portscape-nmap
                     <utilizador> ALL=(root) NOPASSWD: %s
                     e por portscape.nmap.command: ["sudo", "-n", "%s"]
                  2. remover -sS e -O de portscape.nmap.arguments (scan sem privilegios,
                     mas sem detecao de sistema operativo).
                stderr do nmap: %s"""
                .formatted(properties.binary(), properties.binary(), excerpt(stderr));
    }

    private static String excerpt(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return "(vazio)";
        }
        return trimmed.length() <= STDERR_EXCERPT_LIMIT
                ? trimmed
                : trimmed.substring(0, STDERR_EXCERPT_LIMIT) + "... (truncado)";
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }
}
