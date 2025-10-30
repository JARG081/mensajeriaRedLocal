package com.cliente.cliente.connection;

import com.cliente.cliente.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class MessageReceiver {
    private static final Logger log = LoggerFactory.getLogger(MessageReceiver.class);

    private final TcpConnection conn;
    private final MessageService messageService;
    private final ExecutorService exec;

    private volatile boolean running = true;

    @Autowired
    public MessageReceiver(TcpConnection conn, MessageService messageService) {
        this.conn = conn;
        this.messageService = messageService;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "receiver-thread");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void start() {
        log.info("MessageReceiver arrancando");
        exec.submit(this::runLoop);
    }

    private void runLoop() {
        while (running) {
            try {
                if (!conn.isConnected()) {
                    Thread.sleep(200);
                    continue;
                }

                String line;
                try {
                    line = conn.readLine();
                } catch (IOException ioe) {
                    log.warn("IOException leyendo del socket: {}. Marcar desconectado y reintentar.", ioe.getMessage());
                    // readLine() ya deja conn en estado desconectado cuando corresponde
                    Thread.sleep(1000);
                    continue;
                }

                if (line == null) {
                    // conexión cerrada por el servidor; esperar reconexión manual
                    log.info("readLine() devolvió null -> conexión cerrada por el servidor. Esperando reconexión.");
                    Thread.sleep(500);
                    continue;
                }

                // Registrar la línea recibida (INFO para que sea visible)
                log.info("Línea recibida del servidor: {}", line);
                log.info("mensaje llega a funcion de MessageReceiver.runLoop correctamente. Pasando a MessageService.handleServerLine");

                // manejar la línea recibida
                try {
                    messageService.handleServerLine(line);
                } catch (Exception e) {
                    log.error("Error procesando línea del servidor: {}", e.toString(), e);
                }

            } catch (InterruptedException ie) {
                log.info("MessageReceiver interrumpido, saliendo del bucle");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("Error inesperado en MessageReceiver: {}", ex.toString(), ex);
                try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.info("MessageReceiver terminado");
    }

    @PreDestroy
    public void stop() {
        log.info("Deteniendo MessageReceiver");
        running = false;
        exec.shutdownNow();
        try {
            if (!exec.awaitTermination(1, TimeUnit.SECONDS)) {
                log.warn("El executor del MessageReceiver no terminó a tiempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}