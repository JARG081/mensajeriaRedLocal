package com.proyecto.demo.server;

import com.proyecto.demo.ui.UiServerWindow;
import com.proyecto.demo.auth.FileAuthService;
import com.proyecto.demo.factory.ServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
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

    private final FileAuthService authService;
    private final ExecutorService pool = ServerFactory.createCachedThreadPool();
    private Thread serverThread;
    private volatile ServerSocket serverSocket;
    private volatile boolean running = true;

    public TcpServer(FileAuthService authService) {
        this.authService = authService;
    }

    @PostConstruct
    public void startServerThread() {
        // Crear hilo NO-daemon. Spring mantiene el lifecycle; hilo no-daemon evita cierres inesperados.
        serverThread = ServerFactory.createServerThread(this, "servidor");
        serverThread.start();

        // Registrar shutdown hook para cerrar recursos si la JVM termina
        Runtime.getRuntime().addShutdownHook(ServerFactory.createServerThread(() -> {
            try {
                shutdown();
            } catch (Exception ignored) {}
        }, "tcpserver-shutdown-hook"));
    }

    @Override
    public void run() {
        try {
            serverSocket = ServerFactory.createBoundServerSocket(bindAddress, port);
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
                    pool.submit(ServerFactory.createClientWorker(socket, authService));
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
            pool.shutdownNow();
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Executor pool did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("TcpServer shutdown complete");
    }
}
