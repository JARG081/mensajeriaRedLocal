package com.cliente.cliente.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class ClientInfrastructureConfig {

    @Bean(name = "receiverExecutor", destroyMethod = "shutdown")
    public ExecutorService receiverExecutor() {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "receiver-thread");
            t.setDaemon(true);
            return t;
        };
        return Executors.newSingleThreadExecutor(tf);
    }
}
