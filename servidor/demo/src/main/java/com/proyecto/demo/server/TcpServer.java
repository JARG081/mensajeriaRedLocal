package com.proyecto.demo.server;

import com.proyecto.demo.ui.UiServerWindow;
import com.proyecto.demo.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TcpServer implements Runnable {

    @Value("${server.address:0.0.0.0}")
    private String bindAddress;

    @Value("${server.port:8080}")
    private int port;

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final AuthService authService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ExecutorService executorService;
    private final ApplicationContext applicationContext;
    private final ConnectedClients connectedClients;
    private Thread serverThread;
    private volatile ServerSocket serverSocket;
    private volatile boolean running = true;

    public TcpServer(AuthService authService, 
                     org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                     ExecutorService executorService,
                     ApplicationContext applicationContext,
                     ConnectedClients connectedClients) {
        this.authService = authService;
        this.jdbcTemplate = jdbcTemplate;
        this.executorService = executorService;
        this.applicationContext = applicationContext;
        this.connectedClients = connectedClients;
    }

    @PostConstruct
    public void startServerThread() {
        // Crear hilo NO-daemon. Spring mantiene el lifecycle; hilo no-daemon evita cierres inesperados.
        serverThread = new Thread(this, "servidor");
        serverThread.setDaemon(false);
        serverThread.start();

        // Registrar shutdown hook para cerrar recursos si la JVM termina
        Thread shutdownHook = new Thread(() -> {
            try {
                shutdown();
            } catch (Exception ignored) {}
        }, "tcpserver-shutdown-hook");
        shutdownHook.setDaemon(false);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(bindAddress, port));
            log.info("Servidor TCP escuchando en {}:{}", bindAddress, port);

            while (running && !serverSocket.isClosed()) {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                    log.info("Nueva conexión entrante desde {}", socket.getRemoteSocketAddress());

                    boolean approved = true;

                    // Solo pedir aprobación si hay entorno gráfico
                    if (!GraphicsEnvironment.isHeadless()) {
                        approved = UiServerWindow.requestApproval(socket);
                    }

                    if (!approved) {
                        log.warn("Conexión rechazada manualmente desde {}", socket.getRemoteSocketAddress());
                        try { socket.close(); } catch (IOException ignored) {}
                        continue;
                    }

                    log.info("Conexión aprobada desde {}", socket.getRemoteSocketAddress());
                    // Crear ClientWorker usando ApplicationContext para obtener un bean prototype
                    ClientWorker worker = applicationContext.getBean(ClientWorker.class, socket, authService, jdbcTemplate, connectedClients);
                    executorService.submit(worker);
                } catch (IOException e) {
                    if (!running) break; // shutdown in progres
                    log.error("Error aceptando conexión: {}", e.toString(), e);
                }
            }
        } catch (IOException e) {
            log.error("Error en servidor TCP al iniciar: {}", e.toString(), e);
        } finally {
            shutdown();
        }
    }

    @PreDestroy
    public void preDestroy() {
        shutdown();
    }

    private synchronized void shutdown() {
        if (!running) return;
        running = false;
        log.info("Shutting down TcpServer...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
        } catch (Exception ignored) {}
        try {
            executorService.shutdownNow();
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Executor pool did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("TcpServer shutdown complete");
    }
}
