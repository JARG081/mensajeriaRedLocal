package com.proyecto.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuración de beans de infraestructura para el servidor TCP.
 * Reemplaza el patrón de factoría estática ServerFactory por inyección de dependencias.
 */
@Configuration
public class ServerInfrastructureConfig {

    /**
     * Crea un ExecutorService con un pool de threads que crece dinámicamente.
     * Se usa para manejar las conexiones de clientes de forma concurrente.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService executorService() {
        return Executors.newCachedThreadPool();
    }
}
