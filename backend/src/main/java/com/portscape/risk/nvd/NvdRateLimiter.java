package com.portscape.risk.nvd;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.portscape.config.NvdProperties;

/**
 * Espaca os pedidos ao NVD pelo intervalo minimo configurado.
 *
 * <p>Sem API key o limite e 5 pedidos / 30s; com key, 50. Esperar de proposito e
 * preferivel a levar 429 e perder a resposta -- e como os scans correm num unico
 * thread (ver {@code AsyncConfig}), um limitador desta simplicidade chega. A cache
 * e que faz o trabalho pesado: na pratica sao poucos os CPEs que chegam aqui.
 */
@Component
public class NvdRateLimiter {

    private final Duration minInterval;
    private long lastRequestAt;

    public NvdRateLimiter(NvdProperties properties) {
        this.minInterval = properties.minRequestInterval();
    }

    public synchronized void acquire() {
        if (lastRequestAt != 0) {
            long elapsedMs = (System.nanoTime() - lastRequestAt) / 1_000_000L;
            long waitMs = minInterval.toMillis() - elapsedMs;
            if (waitMs > 0) {
                // Thread.sleep e nao wait(): aqui queremos mesmo segurar o monitor,
                // senao outro thread passava a frente e o espacamento nao valia nada.
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastRequestAt = System.nanoTime();
    }
}
