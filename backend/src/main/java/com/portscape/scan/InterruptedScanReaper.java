package com.portscape.scan;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fecha, no arranque, os scans que ficaram a meio.
 *
 * <p>Um scan vive num thread e num processo do nmap: se a aplicacao morrer enquanto
 * ele corre, o processo vai com ela mas a linha na base de dados fica em RUNNING para
 * sempre. O frontend faz polling desse job a espera de um fim que nunca chega.
 *
 * <p>Marcar como FAILED em vez de apagar e deliberado: o utilizador pediu aquele
 * scan, e ver "interrompido" e informacao -- ver o scan desaparecer sem explicacao
 * nao e.
 */
@Component
public class InterruptedScanReaper implements ApplicationRunner {

    public static final String ERROR_CODE = "INTERRUPTED";

    private static final Logger log = LoggerFactory.getLogger(InterruptedScanReaper.class);

    private final ScanJobStore store;
    private final Clock clock;

    public InterruptedScanReaper(ScanJobStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ScanJob> unfinished = store.findUnfinished();
        if (unfinished.isEmpty()) {
            return;
        }
        for (ScanJob job : unfinished) {
            store.save(job.failed(ERROR_CODE,
                    "A aplicacao foi reiniciada enquanto este scan corria. "
                            + "O processo do nmap nao sobrevive ao reinicio; corre o scan outra vez.",
                    clock.instant()));
        }
        log.warn("{} scan(s) tinham ficado por terminar e foram marcados como {}",
                unfinished.size(), ERROR_CODE);
    }
}
