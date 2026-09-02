package com.portscape.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool dedicado aos scans. Deliberadamente com um unico thread: dois scans a
 * correr ao mesmo tempo na mesma rede degradam-se mutuamente e falseiam os
 * resultados. Os pedidos extra ficam em fila em vez de arrancarem em paralelo.
 */
@Configuration
public class AsyncConfig {

    public static final String SCAN_EXECUTOR = "scanExecutor";

    /**
     * Declarado como {@code AsyncTaskExecutor} e nao como {@code Executor} para dar
     * acesso ao {@code submit(...)}: e o {@code Future} que ele devolve que permite
     * cancelar um scan em curso.
     */
    @Bean(SCAN_EXECUTOR)
    public AsyncTaskExecutor scanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("scan-");
        executor.initialize();
        return executor;
    }
}
