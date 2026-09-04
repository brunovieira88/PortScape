package com.portscape.scan;

import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Corre a tarefa no proprio thread de quem a submete.
 *
 * <p>Torna os testes do {@link ScanService} deterministas: o scan acontece dentro da
 * chamada ao {@code startScan}, sem esperas nem sondagens. Uma classe e nao um lambda
 * porque o {@code submit} tem de devolver um {@code Future} ja terminado -- e desse
 * {@code Future} que o servico se serve para cancelar.
 */
class SyncTaskExecutor implements AsyncTaskExecutor {

    @Override
    public void execute(Runnable task) {
        task.run();
    }
}
