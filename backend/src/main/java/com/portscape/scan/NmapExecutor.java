package com.portscape.scan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.portscape.config.NmapProperties;
import com.portscape.scan.exception.ScanException;
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
    private static final String PERCENT_ATTRIBUTE = "percent=\"";

    private final NmapProperties properties;

    /**
     * Threads dedicados a drenar os pipes do processo.
     *
     * <p>Nao usa o {@code ForkJoinPool} comum (o que o {@code supplyAsync} sem
     * executor usaria): estas tarefas sao I/O bloqueante do principio ao fim, e o
     * pool comum e dimensionado para trabalho de CPU e partilhado com o resto da
     * JVM -- dois scans a ler pipes chegavam para o esfomear. Threads daemon, para
     * nao segurarem o encerramento da aplicacao.
     */
    private final ExecutorService pipeReaders = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "nmap-pipe-reader");
        thread.setDaemon(true);
        return thread;
    });

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
        return execute(command, null);
    }

    public String execute(List<String> command, Consumer<Integer> progressListener) {
        log.info("A executar: {}", String.join(" ", command));

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new NmapNotFoundException(
                    "Nao foi possivel executar o nmap em '" + properties.binary()
                            + "'. Confirma que esta instalado e que portscape.nmap.command esta correto.", e);
        }

        CompletableFuture<String> stdout = readAsync(process.getInputStream(), progressListener);
        CompletableFuture<String> stderr = readAsync(process.getErrorStream(), null);


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

    /** Devolve sempre, nunca lanca -- quem chama e que decide, com {@code throw toFailure(...)}. */
    private ScanException toFailure(int exitCode, String stderr) {
        if (requiresRoot(stderr)) {
            return new NmapPrivilegeException(privilegeHelp(stderr));
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

    /**
     * O {@code percent="43.21"} de uma linha {@code <taskprogress>}. Vazio se a linha
     * nao o trouxer ou nao for um numero -- uma barra de progresso nao e razao para
     * reprovar um scan que esta a correr bem.
     */
    private static Optional<Integer> percentOf(String line) {
        int start = line.indexOf(PERCENT_ATTRIBUTE);
        if (start < 0) {
            return Optional.empty();
        }
        start += PERCENT_ATTRIBUTE.length();
        int end = line.indexOf('"', start);
        if (end <= start) {
            return Optional.empty();
        }
        try {
            return Optional.of((int) Double.parseDouble(line.substring(start, end)));
        } catch (NumberFormatException e) {
            log.debug("percent nao numerico em <taskprogress>: {}", line);
            return Optional.empty();
        }
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

    private CompletableFuture<String> readAsync(InputStream stream, Consumer<Integer> progressListener) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (progressListener != null && line.contains("<taskprogress")) {
                        percentOf(line).ifPresent(progressListener::accept);
                    }
                    
                    // O --stats-every 1s injeta <taskprogress> e outras tags no meio da lista de <host>.
                    // O Jackson XML Mapper tem um bug/feitio conhecido: se uma lista (useWrapping=false)
                    // for interrompida por tags alienigenas, ele corta a lista ali mesmo.
                    // A solucao e limpar estas tags de ruido do XML final.
                    if (line.trim().startsWith("<task")) {
                        continue;
                    }
                    
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return sb.toString();
        }, pipeReaders);
    }
}
