package com.cliente.cliente.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServerConfig {

    @Value("${server.remote.ip:${server.address:127.0.0.1}}")
    private String serverIp;

    @Value("${server.remote.port:${server.port:8080}}")
    private int serverPort;

    public String getServerIp() {
        return serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }
}
