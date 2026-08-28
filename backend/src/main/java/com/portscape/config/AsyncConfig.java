package com.portscape.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool dedicado aos scans. Deliberadamente com um unico thread: dois scans a
 * correr ao mesmo tempo na mesma rede degradam-se mutuamente e falseiam os
 * resultados. Os pedidos extra ficam em fila em vez de arrancarem em paralelo.
 */
@Configuration
public class AsyncConfig {

    public static final String SCAN_EXECUTOR = "scanExecutor";

    @Bean(SCAN_EXECUTOR)
    public Executor scanExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("scan-");
        executor.initialize();
        return executor;
    }
}
